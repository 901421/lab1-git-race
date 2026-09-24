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

### Bonus: greeting history

During the lab session on 18 September, the teacher accepted verbally a bonus with a history of greetings stored in a database.

Goal: every greeting is stored in a database that survives a restart, and the history can be read as JSON and on the home page.

I set these rules block by block, each one before the code of that block:
- Only a name sent in the request is stored. A greeting without a name, or with the name from the cookie, is not stored, so reloading the page does not fill the history.
- The page and the API store greetings in the same way.
- The history must never break the greeting. If the database fails, the greeting is still shown.

Success criteria:
- After `/?name=Ana` or `/api/hello?name=Ana`, the greeting is in the database, also after a restart.
- `GET /api/greetings` returns the 10 most recent greetings, newest first.
- The home page lists the same greetings, and a name that contains HTML is shown as plain text.
- The tests never use the database file.

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

### Bonus: greeting history

| Request | Before | After |
|---|---|---|
| `GET /?name=Ana` or `GET /api/hello?name=Ana` | nothing stored | name, requested language and time stored |
| `GET /` with only the `name` cookie, or with no name | nothing stored | still nothing stored |
| `GET /api/greetings` | did not exist | JSON array with the 10 most recent greetings, newest first |
| `GET /` | greeting only | greeting and a "Recent greetings" list |

- `build.gradle.kts`, `libs.versions.toml`: Spring Data JPA, H2, the Kotlin JPA plugin and the JPA test starter. Tests use an in-memory database (`jdbc:h2:mem:testdb`), never the file.
- `application.properties`: H2 in the file `./data/greetings`, `ddl-auto=update` and `open-in-view=false`. `.gitignore`: `data/`.
- New package `history`:
  - `Greeting`: the entity (name, locale, createdAt).
  - `GreetingRepository`: a query for the 10 most recent greetings.
  - `GreetingHistory`: stores and reads greetings. A database error when storing is logged and ignored.
  - `GreetingView`: what the API returns.
  - `GreetingHistoryController`: `GET /api/greetings`.
- `HelloController.kt`: both controllers call `GreetingHistory.record()` for a non-blank name from the request. `welcome()` adds the history to the model, and shows a notice instead if it cannot be read.
- `welcome.html`: the history section. Three new keys `history.*` in both message files.
- Tests: `GreetingRepositoryTests`, `GreetingHistoryTests`, `GreetingHistoryControllerTests`, and new cases in `HelloControllerMVCTests` and `IntegrationTest`.
- Documentation: KDoc on the new classes and on `welcome()` and `helloApi()`; a short note in `README.md`.

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

### Bonus: greeting history

- **H2 in a file, with Spring Data JPA.** The history must survive a restart, so an in-memory database was not enough. Hibernate creates the table from the entity (`ddl-auto=update`). I rejected `schema.sql`, which is more files for one table, and `create-drop`, which deletes the history on every start.
- **Tests use an in-memory database**, set with one line in `build.gradle.kts`. A test `application.properties` would have hidden the main one completely.
- **`Greeting` is a `class`, not a `data class`.** With JPA, the `equals` of a data class changes when the id goes from `null` to a value. The Kotlin JPA plugin creates the empty constructor that JPA needs.
- **Store the name and the language, not the greeting text.** The text can be built again from `messages*.properties`.
- **Store only a name sent in the request.** A name from the cookie is not stored, because every reload of the page would add the same entry again. An empty name is not stored either.
- **Store the language the visitor asked for, without the region** (`es`, not `es-ES`). With `fr` the page answers in English, but I still store `fr`. Rebuilding the text with `fr` gives the same English text, and I keep the information that someone asked for French. Storing the language of the answer would need a second list of supported languages in the code.
- **A small service, `GreetingHistory`**, instead of calling the repository from each controller. The controllers decide *when* to store; the service decides *how*.
- **The history never breaks the greeting.** If storing fails, the error is logged and the greeting is shown. If reading fails on the page, the page answers 200 with a notice instead of the list. I rejected answering 404: the page exists, and 404 means that it does not. I also rejected a custom 404 page, because it is a different feature, UI only, and outside what the teacher accepted.
- **`/api/greetings` returns an error if the database fails.** There the history is the whole resource, and an empty list would say that there are no greetings, which would be false.
- **The API returns `name`, `locale` and `timestamp` in a JSON array**, with a small class (`GreetingView`) instead of the entity. The id is internal, and `timestamp` is the same name that `/api/hello` uses. The limit is fixed at 10; a `?limit=` parameter would need more validation and tests.
- **The page is rendered by the server with Thymeleaf**, not by JavaScript calling `/api/greetings`. The server decides what the page shows, and MockMvc can test it.
- **Times are shown in UTC, with the label "UTC".** The server does not know the visitor's time zone.
- **Names are printed with `th:text`.** A name sent by one visitor is shown to everybody, so it must be escaped. A test checks that `<script>` is shown as text.

## How I verified

### Final state

```bash
./gradlew clean check
```

BUILD SUCCESSFUL, 46 tests, 0 failures: 26 for the increment and 20 for the bonus (see *Bonus: greeting history* below). My machine runs with a Spanish locale, so I also ran the tests with an English JVM, to be sure they do not depend on the machine:

```bash
LANG=en_US.UTF-8 JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" ./gradlew check --rerun-tasks
```

Same result: 46 tests, 0 failures.

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

### Bonus: greeting history

**Final state.** `./gradlew clean check`: BUILD SUCCESSFUL, 46 tests, 0 failures (26 before the bonus). I also ran them with an English JVM in the `Asia/Tokyo` time zone, to be sure that the times on the page are really UTC: same result.

**What failed first.** The first run of `GreetingHistoryTests` failed with a `NullPointerException`. The repository was a Mockito mock, and an unstubbed mock returns `null` from `save()`. Spring Data 4 marks `save()` as never returning `null`, so Kotlin checked the value and failed. The real `save()` never returns `null`, so I fixed the test: the mock now returns the entity it receives.

**Tests that must fail.** For each rule, I broke the code on purpose and checked that a test failed, then restored it:

| Change on purpose | Test that failed |
|---|---|
| Query ordered oldest first | the 10 most recent, newest first |
| No `try/catch` when storing | the greeting does not fail when the database is down |
| `record()` called also for the cookie name | the cookie name and an empty name are not stored |
| No `try/catch` when the page reads the history | the page still works when the history cannot be read |
| `th:utext` instead of `th:text` for the name | HTML in a stored name is escaped |

**Restart.** With the real server, `?name=Ana` (Spanish) and `?name=Luis&lang=fr` stored `Ana`/`es` and `Luis`/`fr`. After a restart, both were still there. A visit with only the `name` cookie and a call to `/api/hello` without a name added nothing.

#### How to run and test the bonus

```bash
rm -rf data          # start with an empty history, so the output below matches
./gradlew bootRun
```

Then, from another terminal (timestamps shortened):

```bash
curl -s -H "Accept-Language: es-ES" "http://localhost:8080/?name=Ana" | grep "<strong>Ana"
# <strong>Ana</strong> ·

curl -s "http://localhost:8080/api/hello?name=Luis&lang=fr"
# {"message":"Hello, Luis!","timestamp":"..."}

curl -s -b "name=Ana" "http://localhost:8080/" > /dev/null   # cookie only: not stored

curl -s "http://localhost:8080/api/greetings"
# [{"name":"Luis","locale":"fr","timestamp":"..."},{"name":"Ana","locale":"es","timestamp":"..."}]
```

Stop the server, start it again with `./gradlew bootRun`, and call `/api/greetings` again: the answer is the same. The database file is `./data/greetings.mv.db`.

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
| Bonus: greeting history | Claude Code (Opus 5.5), with `/practica` and `/memoria-sin-ia` | For each block, options with trade-offs and a recommendation; code and tests after my approval; checks run in my terminal; draft of the bonus sections of this report |

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

### Bonus: greeting history

| Field | Entry |
|---|---|
| Representative prompts | "desarolla este punto y vuelve a proponermelo" (develop this point and propose it again), about which language to store; "que quieres decir con eso de los 10 most recent ones, que la bd se sobreescribe?" (what do you mean by "the 10 most recent ones", is the database overwritten?); "seria un error 404 puede ser, analiza esto" (maybe it should be a 404 error, analyse this); "antes de proceder explicame exactamente que devuelve con un ejemplo calro secuencia real" (before going on, explain exactly what it returns with a clear example, a real sequence) |
| Affected files/sections | Package `history` (5 classes), `HelloController.kt`, `welcome.html`, `messages*.properties`, `application.properties`, `build.gradle.kts`, `libs.versions.toml`, `.gitignore`, the test classes, `README.md` and the bonus sections of this report (commits `2118a76` to the one that adds this table, on the branch `feature/greeting-history`) |
| Validation steps | `./gradlew clean check` after each block (26, 28, 36, 40 and 46 tests), with a Spanish and an English JVM; each rule broken on purpose to see its test fail; `curl` against the real server, including a restart; the commands in *How to run and test the bonus* run as written |
| Citations | None. Spring and Thymeleaf APIs were checked in the jars of the versions used (for example the package of `@DataJpaTest` in Boot 4.1 and `#temporals.format`) |
| Human-reviewed | I chose every design decision from the options, 15 in total (what to store, the API shape, how the page shows the history, where the documentation goes). I asked for more analysis twice: which language to store, and my own idea of answering 404 with a custom page, which I dropped after the analysis. I asked to reword a commit message that could be misread, and approved each block and each commit before it was made |
