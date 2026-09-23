# Lab 1 Git Race -- Project Report

## What I specified

I split the increment into three pieces and wrote down the goal and the success criteria of each one before writing any code.

### Piece 1: Locale-aware greeting

Goal: the greeting follows the visitor's language (English and Spanish). The server reads it from the `Accept-Language` header, a `?lang=` parameter can override it, and the choice is remembered on the next visits.

Success criteria:
- `/` shows the English or the Spanish text for `Accept-Language: en` or `es`.
- `?lang=es` switches the language, and the next request without the parameter stays in Spanish.
- `/api/hello` uses the same language as the page.

### Piece 2: Name validation

Goal: the `name` parameter accepted any string of any length. The project already had `spring-boot-starter-validation` but did not use it. I wanted a length limit with Bean Validation and a controlled answer when the limit is broken: a friendly message on the page and a JSON error on the API, both with HTTP 400, never a stack trace.

Success criteria:
- A name of 50 characters is accepted; 51 characters returns 400 on `/` and on `/api/hello`.
- The error text is shown in the visitor's language.

### Piece 3: Remember the last valid name

Goal: the locale was remembered between visits, but the name was not. A valid name should be stored in a cookie and used when the visitor comes back.

Success criteria:
- A valid `?name=` sets a `name` cookie.
- A later visit without `name` greets with the stored name and fills the name field.
- An explicit empty `?name=` shows the generic greeting and does not change the cookie.
- A name that is too long still fails as in Piece 2.

### Review fixes (after the three pieces)

After the three pieces were finished, I reviewed the whole increment against the lab guide. I tested it with plain `curl` and with unusual input, and some cases failed. These criteria were not in my first plan; I set them for the fixes:
- The app gives the same answer on any machine. The server's own locale must not decide the language.
- Normal input never causes an HTTP 500. This includes names with spaces or accents (for example "José María") and a malformed `?lang=`.
- Every behaviour I added has at least one test.

## What I changed

### Behaviour, in short

| Request | Before | After |
|---|---|---|
| `GET /` | English text | English or Spanish. Order: `?lang=`, then the `lang` cookie, then `Accept-Language`, then English |
| `GET /api/hello?name=Ana` | `Hello, Ana!` | `Hello, Ana!` or `¡Hola, Ana!`, same rules as the page |
| `GET /api/hello` (no name) | `Hello, World!` | `Hello, World!` or `¡Hola, Mundo!` |
| `name` longer than 50 characters | accepted | HTTP 400: JSON `{"error": ...}` on the API, red alert on the page |
| `GET /?name=Ana`, later `GET /` | generic greeting | `Hello, Ana!` (name remembered in a cookie, page only) |

### Piece 1: Locale-aware greeting

- New `config/WebConfig.kt`: a `CookieLocaleResolver` (cookie `lang`, 30 days) and a `LocaleChangeInterceptor` for the `lang` query parameter.
- New `messages.properties` and `messages_es.properties` with the greeting texts. They replace the fixed `app.message` property.
- `controller/HelloController.kt`: both controllers get the text from `MessageSource` with the current locale.
- `application.properties`: removed `app.message`; added `spring.messages.fallback-to-system-locale=false`.
- Removed `META-INF/additional-spring-configuration-metadata.json`. It only described `app.message`.
- Tests: the three test classes set the locale of each request. New cases for Spanish and for `?lang=`.

### Piece 2: Name validation

- `HelloController.kt`: `@Validated` on both controllers and `@Size(max = MAX_NAME_LENGTH)` on `name`, with `MAX_NAME_LENGTH = 50`.
- New `controller/ValidationExceptionHandler.kt`: a `@ControllerAdvice` that catches `ConstraintViolationException` and answers 400. It returns JSON for `/api/...` and the page with an error message for `/`.
- New key `name.tooLong` in both message files.
- `templates/welcome.html`: a Bootstrap alert shown only when there is an error.
- `HelloControllerMVCTests.kt`: two cases for a name over the limit (page and API).

### Piece 3: Remember the last valid name

- `HelloController.kt`: `welcome()` reads the `name` cookie with `@CookieValue` and writes it with `ResponseCookie` (30 days) when the name is valid and not blank. `/api/hello` does not use this cookie.
- `HelloControllerMVCTests.kt`: three cases (the cookie is set, the cookie is used, an empty `name` ignores the cookie).
- `HelloControllerUnitTests.kt`: removed two tests that called `welcome()` directly. They stopped compiling when `welcome()` needed the HTTP response, and the MVC tests already covered the same cases.

### Documentation

- KDoc on `HelloController`, `HelloApiController` and `ValidationExceptionHandler` (`WebConfig` already had it).
- `README.md`: a short section, `My increment`, with two example commands. The rest of the README is unchanged.

### Review fixes (after the three pieces)

- `WebConfig.kt`: without an `Accept-Language` header the locale is English, not the server's own locale (`setDefaultLocaleFunction`). A malformed `?lang=` is ignored (`isIgnoreInvalidLocale = true`).
- `application.properties`: corrected the comment about `fallback-to-system-locale`. It promised more than the property does.
- `HelloController.kt`: the default name of `/api/hello` comes from the new key `greeting.defaultName` ("World" / "Mundo"). The name is URL-encoded before it goes into the cookie.
- Tests: one integration test without `Accept-Language`, and MVC tests for the default name in both languages, names with spaces and accents, a malformed `lang`, the `lang` cookie, and an unsupported language (`fr`). I also removed a test field that still read the old `app.message` property.

## Technical decisions

Each point says what I chose, what I rejected and why.

### Piece 1: Locale-aware greeting

- **Spring i18n (`MessageSource` + `LocaleResolver`)** instead of reading `Accept-Language` by hand. The texts stay out of the code, and adding a language only needs a new `messages_xx.properties` file.
- **`CookieLocaleResolver`** instead of `SessionLocaleResolver`. A session ends when the browser closes; the cookie keeps the language for 30 days, which was my success criterion.
- **Localize `/api/hello` too.** Only the page needed it, but a page and an API that answer in different languages would be confusing.
- **`fallback-to-system-locale=false`.** With the default value (`true`), a language without its own file fell back to the server's locale. On my Spanish machine, `?lang=en` returned Spanish, because there is no `messages_en.properties`. Now it falls back to `messages.properties`, which is English.

### Piece 2: Name validation

- **`@Size` on the `@RequestParam`, with `@Validated` on the class**, instead of a DTO with `@Valid` or a manual `if`. It is the smallest change that really uses Bean Validation, and there is only one field. `@Validated` is needed because Spring does not validate single method parameters without it.
- **Error text from `MessageSource`, not from Bean Validation's own message.** Bean Validation uses the JVM's locale, not the request's, so the error would not follow the visitor's language.
- **Page or JSON chosen by the `/api/` prefix of the path**, instead of content negotiation with the `Accept` header. There are only two fixed routes. The limit: it would stop working if the app ran under a context path.
- **Limit of 50 characters** in one constant, used by the annotation and by the error message.

### Piece 3: Remember the last valid name

- **`@CookieValue` and `ResponseCookie` in the controller**, instead of an interceptor like the one for the locale. For one optional field, an interceptor was more code than needed.
- **An empty `?name=` does not use the cookie; a missing one does.** It follows the same idea as `?lang=`: an explicit value in the request wins. In the code, `null` (missing) and `""` (empty) are different cases.
- **Page only, not `/api/hello`.** An API call should give the same answer to the same request, so every call must send its own name.
- **Removed two unit tests** instead of rewriting them with a mock response (`MockHttpServletResponse`). The MVC tests already covered them and also check the cookie. Rewriting them would have been cheap too; I chose not to keep two tests for the same thing.

### Review fixes

- **English when there is no `Accept-Language` header**, using `setDefaultLocaleFunction`. I rejected two options: a fixed `setDefaultLocale`, which ignores the header for everybody, and leaving it as it was, where plain `curl` got Spanish on my machine and English on others.
- **URL-encode the name in the cookie with `UriUtils.encode`.** My first plan was `URLEncoder` plus `URLDecoder`. I dropped it when a test showed that `@CookieValue` already decodes `%XX`, so decoding again by hand would decode twice.
- **Translate the default name ("World" / "Mundo").** Before, Spanish showed "¡Hola, World!". An empty `?name=` still gets the default name, as in the starter.
- **Ignore a malformed `?lang=`** (`isIgnoreInvalidLocale`) instead of letting it fail with a 500.

## How I verified

### Final state

```bash
./gradlew clean check
```

BUILD SUCCESSFUL, 26 tests, 0 failures. My machine runs with a Spanish locale, so I also ran the tests with an English JVM, to be sure they do not depend on the machine:

```bash
LANG=en_US.UTF-8 JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" ./gradlew check --rerun-tasks
```

Same result: 26 tests, 0 failures.

### What failed first, and what I fixed

**Piece 1.** I found two bugs by running the app:
- A fixed default locale on the resolver (`setDefaultLocale`) made it ignore `Accept-Language`. I removed it. I checked this again later: with `setDefaultLocale(Locale.ENGLISH)` put back, `Accept-Language: es` returned "Hello, Ana!".
- With Spring Boot's default `fallback-to-system-locale=true`, `?lang=en` returned Spanish on my machine. I set it to `false`. I checked this again later by starting the app with `--spring.messages.fallback-to-system-locale=true`: `?lang=en` and `Accept-Language: fr` both returned "¡Hola, Ana!".

In MockMvc, `.header("Accept-Language", ...)` did not change the locale; I had to use `.locale(...)` on the request.

**Piece 2.** `./gradlew clean check`: BUILD SUCCESSFUL, 17 tests at that point. I also checked with `curl` that 50 characters are accepted and 51 are rejected, on the page and on the API.

**Piece 3.** The new signature of `welcome()` broke the compilation of `HelloControllerUnitTests`. I removed the two tests that called it (see *Technical decisions*). Then `./gradlew clean check` passed with 18 tests.

**Review fixes.** For each fix I first wrote a test and saw it fail, then changed the code:

| Commit | Test that failed first | Error before the fix |
|---|---|---|
| `8ad4a0a` | `/api/hello?name=Test` with no `Accept-Language` | `¡Hola, Test!` instead of `Hello, Test!` |
| `f231564` | `/api/hello` with no name, in Spanish | `¡Hola, World!` instead of `¡Hola, Mundo!` |
| `fccffb4` | `/?name=José María` | HTTP 500: `RFC2616 cookie value can only have US-ASCII chars` |
| `6e2f253` | `/?lang=;;` | HTTP 500: `Locale part ";;" contains invalid characters` |

After the cookie fix I also checked it with the real server. With `curl`, the names `José María`, `Ana;x`, `Ana,x`, `Ana"x` and `100%` were stored and read back correctly on the next request.

### How to run and test

```bash
./gradlew bootRun
```

Then, from another terminal (timestamps shortened):

```bash
curl -s -H "Accept-Language: es" "http://localhost:8080/api/hello?name=Ana"
# {"message":"¡Hola, Ana!","timestamp":"..."}

curl -s "http://localhost:8080/api/hello"
# {"message":"Hello, World!","timestamp":"..."}

curl -s -H "Accept-Language: es" "http://localhost:8080/api/hello"
# {"message":"¡Hola, Mundo!","timestamp":"..."}

curl -s -w " HTTP %{http_code}\n" "http://localhost:8080/api/hello?name=$(python3 -c 'print("a"*51)')"
# {"error":"Name must be at most 50 characters."} HTTP 400

curl -s -i "http://localhost:8080/?lang=es" | grep -i "^set-cookie"
# Set-Cookie: lang=es; Path=/; Max-Age=2592000; Expires=...; SameSite=Lax

curl -s -i "http://localhost:8080/?name=Jos%C3%A9%20Mar%C3%ADa" | grep -i "^set-cookie"
# Set-Cookie: name=Jos%C3%A9%20Mar%C3%ADa; Path=/; Max-Age=2592000; Expires=...

curl -s -b "name=Ana" "http://localhost:8080/" | grep "lead"
# <p class="lead mb-4">Hello, Ana!</p>
```

### Properties and cookies

| Item | Where | Value |
|---|---|---|
| `spring.messages.fallback-to-system-locale` | `application.properties` | `false` |
| `greeting.default`, `greeting.named`, `greeting.defaultName`, `name.tooLong` | `messages.properties`, `messages_es.properties` | English / Spanish texts |
| Cookie `lang` | set by `?lang=` | 30 days, `Path=/` |
| Cookie `name` | set by a valid `?name=` on `/` | 30 days, `Path=/`, URL-encoded |
| Maximum name length | `HelloController.MAX_NAME_LENGTH` | 50 |

## AI disclosure

**Methodology:** every AI-assisted increment in this lab follows a fixed process agreed with the assistant before writing any code, kept in a local `CLAUDE.md` file (working rules only, not part of this submission): the assistant states the problem, proposes several general solutions with their trade-offs, I pick one, and each file is then proposed and only applied after my explicit confirmation. Progress and AI-usage notes are tracked between sessions in a local `STATUS.md` (not submitted either), which is what this disclosure is built from. `CLAUDE.md` itself was not written by me alone beforehand — it was drafted together with the assistant, in this same conversation, before any lab code existed.

### Piece 1 — Locale-aware greeting

- **Tools:** Claude Code (Sonnet 5).
- **Purpose:** assisted implementing this increment through a guided flow: stating the problem, proposing several general solutions with trade-offs, then proposing each file's content one at a time for approval before writing.
- **Representative prompts:** "propusieras el problema primero, propusieras posibles soluciones y elijo una... y me propones bloques de código a modificar para cada fichero"; per-file "sí" / "aplica este bloque" confirmations.
- **Affected files/sections:** see "What I changed" above.
- **Validation steps:** `./gradlew check` and manual `curl` checks, run and read by me.
- **Human-reviewed:** I picked the cookie-based resolver over the two alternatives presented, decided to also localize `/api/hello`, and approved each file individually before it was written.
- **Provenance note:** this exact increment had already been implemented and verified in an earlier session; a local git/repository incident (fork identity, unrelated to this code) required rebuilding the repository from scratch. The assistant kept a private reference of the previous diff to avoid re-deriving trivial syntax, but the problem, the alternatives and the decision were walked through again with me before any file was written, not reapplied blindly.

### Piece 2 — Name validation

- **Representative prompts:** "elijo B, esa regla vale" (choosing the solution after trade-offs were presented); per-file "sí, aplícalo" confirmations.
- **Affected files/sections:** see "What I changed" above.
- **Validation steps:** `./gradlew clean check` and manual `curl` checks, run and read by me.
- **Human-reviewed:** I picked option B over the DTO and manual alternatives, and approved the locale-handling decision for error messages (avoiding Bean Validation's own interpolator) before it was implemented.

### Piece 3 — Remember the last valid name (optional)

- **Representative prompts:** "sí, elijo B, solo la página" (choosing the solution and scoping it); "opción 2 sí es la correcta" (choosing how to resolve the broken unit tests, after both options were explained).
- **Affected files/sections:** see "What I changed" above.
- **Validation steps:** `./gradlew clean check` and manual `curl` checks, run and read by me.
- **Human-reviewed:** I picked option B over the manual and interceptor alternatives, scoped the feature to the page only, and decided how to resolve the compile break the change introduced.
