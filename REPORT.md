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

**Method.** I used Claude Code as an assistant in every step, under fixed rules that I wrote in a local `CLAUDE.md` file (not part of the submission). For each piece I first wrote the goal and the success criteria (see *What I specified*). The assistant then described the problem, proposed two or three solutions with their trade-offs, and after I chose one, proposed the code block by block. No block was written until I approved it. The assistant ran `./gradlew check` and the `curl` commands in the terminal of my machine, and I read their output before approving each step. Prompts were in Spanish; I quote them literally, with a translation.

Summary of the uses:

| Phase | Tools / skills | Purpose |
|---|---|---|
| Piece 1: locale | Claude Code (Sonnet 5) | Alternatives for i18n and code proposed block by block |
| Piece 2: validation | Claude Code (Sonnet 5) | Alternatives for validation and code proposed block by block |
| Piece 3: name cookie | Claude Code (Sonnet 5) | Alternatives for the cookie; how to handle the broken unit tests |
| Documentation | Claude Code (Sonnet 5) | KDoc drafts and the `README.md` note |
| Review fixes | Claude Code (Opus 5.5), with a local lab-workflow skill (`/practica`) | Check the increment against the guide, test edge cases, find bugs, propose tests and fixes |
| Report | Claude Code (Opus 5.5), with a local writing-style skill (`/memoria-sin-ia`) | Draft sections 1 to 4 of this report from the facts in the repository, in plain English |

### Piece 1: Locale-aware greeting

| Field | Entry |
|---|---|
| Representative prompts | "propusieras el problema primero, propusieras posibles soluciones y elijo una... y me propones bloques de código a modificar para cada fichero" (state the problem first, propose possible solutions and I choose one, then propose the code blocks to change in each file); then a "sí" for each block |
| Affected files/sections | `WebConfig.kt`, `HelloController.kt`, `messages*.properties`, `application.properties`, the three test classes |
| Validation steps | `./gradlew check` and `curl` against the running app. Running the code showed two bugs (fixed default locale, `fallback-to-system-locale`) that I fixed before the commit |
| Citations | None |
| Human-reviewed | I chose the cookie-based resolver over the session and the manual options, decided to localize `/api/hello` too, and approved each file |

### Piece 2: Name validation

| Field | Entry |
|---|---|
| Representative prompts | "elijo B, esa regla vale" (I choose B, that rule is fine); then a "sí, aplícalo" (yes, apply it) for each block |
| Affected files/sections | `HelloController.kt`, `ValidationExceptionHandler.kt`, `messages*.properties`, `welcome.html`, `HelloControllerMVCTests.kt` |
| Validation steps | `./gradlew clean check` (17 tests at that point) and `curl` with 50 and 51 characters, on the page and on the API |
| Citations | None |
| Human-reviewed | I chose `@Size` on the parameter over a DTO or a manual check, and approved resolving the error text with `MessageSource` instead of Bean Validation's own message |

### Piece 3: Remember the last valid name

| Field | Entry |
|---|---|
| Representative prompts | "sí, elijo B, solo la página" (yes, I choose B, only the page); "opción 2 sí es la correcta" (option 2 is the right one), about the broken unit tests |
| Affected files/sections | `HelloController.kt`, `HelloControllerMVCTests.kt`, `HelloControllerUnitTests.kt` |
| Validation steps | `./gradlew clean check` (18 tests) and `curl` with and without the `name` cookie |
| Citations | None |
| Human-reviewed | I chose the controller option over an interceptor, limited the cookie to the page, and chose to remove the two broken unit tests |

### Documentation

| Field | Entry |
|---|---|
| Representative prompts | Not recorded |
| Affected files/sections | KDoc in `HelloController.kt` and `ValidationExceptionHandler.kt`; the `My increment` section of `README.md` |
| Validation steps | `./gradlew check`; I read the KDoc against the code |
| Citations | None |
| Human-reviewed | I approved the texts and kept the README change to one short section |

### Review fixes

| Field | Entry |
|---|---|
| Representative prompts | "Esto quiero que lo analices a fondo tendria que ser Hello World, Hola Mundo" (analyse this in depth, it should be Hello World, Hola Mundo); "ok a los tres, empieza por el fallo 1" (ok to the three, start with bug 1) |
| Affected files/sections | `WebConfig.kt`, `HelloController.kt`, `application.properties`, `messages*.properties`, `IntegrationTest.kt`, `HelloControllerMVCTests.kt` (commits `8ad4a0a` to `ec08b34`) |
| Validation steps | For each fix, a test written first and seen failing (see *How I verified*); then `./gradlew check` with a Spanish and an English JVM (26 tests) and `curl` against the real server |
| Citations | None. Spring APIs were checked in the Spring 7 jars (`javap`) and by running the app |
| Human-reviewed | I chose English when there is no `Accept-Language` over leaving it as it was, asked for "World" / "Mundo", and approved each fix and each commit. I rejected the assistant's first plan for the cookie (`URLEncoder` + `URLDecoder`) after a test showed that it would decode twice |

### Report

| Field | Entry |
|---|---|
| Representative prompts | "ok, adelante con What I changed" (ok, go on with What I changed); "ok, verifica ese dato y monta el REPORT" (ok, check that fact and put the report together) |
| Affected files/sections | `REPORT.md`, sections *What I specified*, *What I changed*, *Technical decisions*, *How I verified* and this disclosure |
| Validation steps | Every command in the report was run as written. Two facts about Piece 1 were checked again by running the app |
| Citations | None |
| Human-reviewed | I approved each section. Two sentences in the drafts were removed because they were not true |
