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

### Bonus: live updates (SSE)

After the greeting history was finished, the teacher asked for a second extra: show the stored greetings live on the home page.

Goal: when a greeting is stored, every open home page adds it to the "Recent greetings" list without a reload.

I set the first rules before writing any code, and the rest in the block where they were needed:
- A greeting is sent live only after it is stored. The live view never shows a greeting that the database does not have.
- A page that connects late, or reconnects, first gets the greetings it missed (at most 10), and never gets the same greeting twice.
- Closed pages must not leave open connections on the server.
- Each live greeting has the same JSON as in `/api/greetings`, without the id.
- Names are still shown as text, never as HTML.

Success criteria:
- With two tabs open on `/`, a greeting sent from one tab, or with `curl`, appears at the top of the list in the other tab.
- `curl -N "/api/greetings/stream?after=0"` first sends the stored greetings, and then each new one when it is stored.
- A client that reconnects with `Last-Event-ID` gets only the greetings after that id.
- A connection that fails or times out is removed.
- If the database cannot be read, the stream fails like `/api/greetings`.

### Bonus: connection pool

After the live updates, the teacher recommended HikariCP for concurrent connections to the database.

Goal: many requests at the same time can store and read greetings, and the pool is visible in the configuration.

Success criteria:
- The app uses HikariCP, and the pool is set in `application.properties`.
- 20 requests that arrive at the same time store their 20 greetings, with no errors and no duplicates.

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

### Bonus: live updates (SSE)

| Request | Before | After |
|---|---|---|
| `GET /api/greetings/stream` | did not exist | Server-Sent Events stream: one `greeting` event per stored greeting, with the database id as event id and the same JSON as `/api/greetings` |
| same, with `?after=<id>` or the `Last-Event-ID` header | did not exist | first the greetings stored after that id (at most 10, oldest first), then the new ones |
| `GET /?name=Ana` or `GET /api/hello?name=Ana` | greeting stored | greeting stored, then sent to every open stream |
| `GET /` | list rendered by the server | same list; a script opens the stream and adds new greetings at the top, without a reload |
| `GET /api/greetings` | JSON array without id | unchanged |

- New `GreetingStream`: keeps the open connections and sends each greeting to all of them. A connection is removed when it completes, times out (30 minutes), fails, or a send to it fails.
- `GreetingHistoryController`: `GET /api/greetings/stream`. It registers the connection before it reads the missed greetings, so a greeting stored in between is not lost.
- `GreetingRepository`: a query for the 10 most recent greetings after an id. `GreetingHistory`: `since(id)` for the missed greetings, and `record()` publishes a greeting only after `save()` worked.
- `Greeting`: the time is cut to microseconds when the greeting is created (see *How I verified*).
- `GreetingView`: now also has the id, hidden from the JSON. `Greeting.toView()` builds it in one place.
- `HelloController.kt`: `welcome()` adds `lastGreetingId` to the model, the newest id on the page (0 if the list is empty). It is missing if the history cannot be read, and then the page has no live updates.
- `welcome.html`: the list is always rendered, with `data-last-id`, and every item has a `data-id`. It loads the new script.
- New `static/js/greeting-stream.js`: opens the stream with `EventSource` from `lastGreetingId`, skips ids already on the page, writes names with `textContent`, and keeps at most 10 items.
- Tests: new `GreetingStreamTests`, and new cases in `GreetingRepositoryTests`, `GreetingHistoryTests`, `GreetingHistoryControllerTests`, `HelloControllerMVCTests` and `IntegrationTest`.
- Documentation: KDoc on the new code, and a short note with a `curl -N` example in `README.md`.

### Bonus: connection pool

- `application.properties`: `spring.datasource.hikari.pool-name=greetings-pool` and `maximum-pool-size=10`, with a comment.
- `IntegrationTest`: a test that sends 20 requests to `/api/hello` at the same time and checks that the 20 names are stored.

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

### Bonus: live updates (SSE)

- **Server-Sent Events, with Spring's `SseEmitter`.** This is what the teacher asked for, and it fits: data only goes from the server to the page, over plain HTTP, and the browser reconnects by itself.
- **A separate URL, `GET /api/greetings/stream`.** It is easy to test with `curl -N`, and `/api/greetings` does not change. I rejected one URL that answers JSON or a stream depending on the `Accept` header.
- **Publish only after `save()` worked.** The live view never shows a greeting that the database does not have. If storing fails, nothing is sent, as in the greeting history.
- **The database id is the event id.** The page connects with `?after=<last id shown>`, and when the browser reconnects it sends `Last-Event-ID`, which wins over `after`. The server registers the connection first and then sends what was missed, so a greeting stored in between is not lost. It can arrive twice, and the page drops it by its id. The JSON in `data:` still has no id.
- **At most 10 missed greetings, the most recent ones.** It is the same limit as `/api/greetings`, and `?after=0` does not send the whole database. The query reads newest first and the list is then reversed. Reading oldest first would lose the newest greetings when more than 10 were missed. I rejected sending all missed greetings.
- **Connections in a `CopyOnWriteArrayList`**, removed when they complete, time out, fail, or a send fails. Each greeting reads the list, and it changes only when a page opens or closes. The list can be read while another thread removes a connection. Closed tabs do not pile up.
- **A timeout of 30 minutes.** A client that disappears without closing the connection does not keep it forever; a real browser reconnects with `Last-Event-ID`. I rejected no timeout.
- **If the database cannot be read for the missed greetings, the request fails (500)** and the connection is removed, like `/api/greetings`. I rejected going on without the missed greetings, because the page would have a gap and not know it. I also rejected closing the stream with an error after opening it, because that is harder to test.
- **The time is cut to microseconds when a greeting is created.** H2 stores microseconds, so without this the stream and `/api/greetings` gave different times for the same greeting. I rejected reading the greeting again after `save()` (one more query), and only documenting the difference.
- **The page gets the newest id from the same list it shows.** `GreetingView` has the id, hidden from the JSON with `@JsonIgnore`, and `welcome()` takes the highest one. I rejected a separate query for the last id: a greeting stored between the two queries would be missing from the page. I also rejected a second view class only for the page.
- **The script only adds greetings that arrive later.** The first list is still rendered by the server. The script writes names with `textContent`, never `innerHTML`, for the same reason as `th:text`. I did not add JavaScript tests, because they need a different test setup; I checked the script in the browser instead (see *How I verified*).
- **A new branch, `feature/greeting-stream`**, separate from the greeting history, which was already merged.

### Bonus: connection pool

- **Keep HikariCP and set it explicitly.** Spring Boot already uses HikariCP with Spring Data JPA (`spring-boot-starter-data-jpa` → `spring-boot-starter-jdbc` → `HikariCP 7.0.2`), so no new dependency was needed. I set the pool in `application.properties` so that it is visible, and gave it a name so that it is easy to find in the log.
- **A pool of 10, the default value.** I did not change the size or the timeouts, because I had no load test to justify other numbers. With `open-in-view=false`, a request uses a connection only while its query runs, and an open live stream does not keep one.
- **A test with more requests than connections (20 against 10).** The requests start together with a `CountDownLatch`. The test checks the database, not only the HTTP status, because `/api/hello` answers 200 even when storing fails. I rejected only explaining it in the report, and only changing the configuration without a test.

## How I verified

### Final state

```bash
./gradlew clean check
```

BUILD SUCCESSFUL, 67 tests, 0 failures: 26 for the increment, 20 for the greeting history, 20 for the live updates and 1 for the connection pool (see the bonus sections below). My machine runs with a Spanish locale, so I also ran the tests with an English JVM, to be sure they do not depend on the machine:

```bash
LANG=en_US.UTF-8 JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" ./gradlew check --rerun-tasks
```

Same result: 67 tests, 0 failures.

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

**At the end of this bonus**, before the live updates, `./gradlew clean check` gave BUILD SUCCESSFUL, 46 tests, 0 failures (26 before the bonus). I also ran them with an English JVM in the `Asia/Tokyo` time zone, to be sure that the times on the page are really UTC: same result.

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

### Bonus: live updates (SSE)

**At the end of this bonus**, before the connection pool, `./gradlew clean check` gave BUILD SUCCESSFUL, 66 tests, 0 failures (46 before the live updates).

**What failed first.**
- The end-to-end test in `IntegrationTest` opened the stream with `HttpClient.send()` and timed out. `send()` waits for the response headers, and Spring sends the headers of a stream together with the first event, not when the connection opens. I changed only the test: it opens the stream with `sendAsync()`, waits until the server has registered the connection, and then stores a greeting. It passed three runs in a row. I did not change the endpoint to send an empty first event.
- With the real server, the same greeting had two different times: `…821914673Z` in the stream and `…821915Z` in `/api/greetings`. The stream sent the time from Java (nanoseconds), and H2 stores microseconds, rounded. Since then the time is cut to microseconds when the greeting is created, and a test checks that the database gives back exactly the same time.

**Tests that must fail.**

| Change on purpose | Test that failed |
|---|---|
| No `remove` when a send fails | should forget a connection when sending to it fails |
| Query ordered oldest first | should return the 10 most recent greetings after an id |
| No `reversed()` after the query | should return the missed greetings oldest first |
| No `remove` in `forget()` | should fail and forget the connection when the database is down |
| No `remove` in `onCompletion` | should forget the connection when the browser closes it |
| Publish also when `save()` fails | should not publish when the greeting could not be stored |
| No `publish()` | should publish the greeting once it is stored, should push a new greeting to an open stream |
| No `truncatedTo` | should read back exactly the time it was created with |
| No `@JsonIgnore` on the id | should return the history as a JSON array, newest first |
| No `?: 0L` for an empty history | should start the live stream from 0 when the history is empty |
| `data-last-id` also when the history cannot be read | should not start the live stream when the history cannot be read |

**Not covered by automatic tests.** The `onTimeout` and `onError` callbacks. The real 500 when the database is down: MockMvc throws the exception again instead of returning the response, so the test checks the exception and that the connection is removed. The script in the page: there is no JavaScript test setup, so I ran `node --check` and tested it in the browser.

**In the browser.** With an empty database, I opened `/` in two tabs. Each name sent from the first tab appeared at the top of the list in the second tab, without a reload. A name with HTML, `<b>Eva</b>`, was shown as text. After more than 10 names, the second tab still showed 10 items and no name twice. After a reload, the second tab showed the same list.

#### How to run and test the live updates

```bash
rm -rf data
./gradlew bootRun
```

From a second terminal, keep a stream open:

```bash
curl -N "http://localhost:8080/api/greetings/stream"
```

From a third terminal:

```bash
curl -s "http://localhost:8080/api/hello?name=Ana" > /dev/null
curl -s "http://localhost:8080/api/hello?name=Eva" > /dev/null
```

The stream shows both greetings when they are stored:

```
event:greeting
id:1
data:{"name":"Ana","locale":"en","timestamp":"..."}

event:greeting
id:2
data:{"name":"Eva","locale":"en","timestamp":"..."}
```

A client that reconnects after id 1 gets only what it missed:

```bash
curl -N -H "Last-Event-ID: 1" "http://localhost:8080/api/greetings/stream?after=0"
# event:greeting / id:2 / data:{"name":"Eva",...}
```

In the browser, open `http://localhost:8080/` in two tabs and send a name from one of them: it appears at the top of the list in the other tab.

### Bonus: connection pool

**Final state.** `./gradlew clean check`: BUILD SUCCESSFUL, 67 tests, 0 failures (66 before).

**The pool.** Before the change, the log of `./gradlew bootRun` showed `HikariPool-1 - Start completed.` After it, the log shows `greetings-pool - Start completed.` The tests use the same pool, with the in-memory database.

**A change that did not make the test fail.** I set the pool to 1 connection and the connection timeout to 250 ms, the lowest value HikariCP accepts, and ran the test again. It still passed, with no timeouts: each query is so short that the 20 requests only wait in a queue. So the test shows that 20 requests at the same time store all their greetings, with no errors and no duplicates. It does not show that 10 connections are needed; that would need a load test with slow queries.

## AI disclosure

**Method.** I used Claude Code as an assistant during the whole lab. I wrote the working rules in a local `CLAUDE.md` file (not part of the submission). `/practica` and `/memoria-sin-ia`, named in the table below, are my own commands: generic prompts that I wrote and use for all my lab assignments. `/practica` sets the working rules (follow the lab guide, go step by step, give options with a recommendation, wait for my ok). `/memoria-sin-ia` gives style rules for report text: plain, direct sentences, without filler or the stock phrases of generated text.

Every change followed the same loop:
1. I described what I wanted to change and the success criteria (see *What I specified*). The assistant stated the problem back as it understood it, so that I could correct it.
2. I asked it for possible solutions. It answered with two or three options, the trade-offs of each one and a recommendation.
3. I compared the options and chose one. Sometimes I asked for more analysis first, or proposed my own idea and asked the assistant to analyse it.
4. The assistant proposed the change in small steps, one file or one block at a time. Before doing anything, it told me what it had understood and what it was going to do, and waited for my ok. I approved each step or corrected it. From 23 September this rule also covered reading files and running commands.
5. After each step, the assistant ran `./gradlew check` and the `curl` commands on my machine, and I read the output before approving the commit.

The tables below show, for each phase, what I asked, what I decided, and what failed on the way. Prompts were in Spanish. I quote them with the spelling corrected, followed by an English translation.

Summary of the uses:

| Phase | Tools / skills | Purpose |
|---|---|---|
| Piece 1: locale | Claude Code (Sonnet 5) | Alternatives for i18n and code proposed block by block |
| Piece 2: validation | Claude Code (Sonnet 5) | Alternatives for validation and code proposed block by block |
| Piece 3: name cookie | Claude Code (Sonnet 5) | Alternatives for the cookie; how to handle the broken unit tests |
| Documentation | Claude Code (Sonnet 5) | KDoc drafts and the `README.md` note |
| Review fixes | Claude Code (Opus 5.5), with my own `/practica` command | Check the increment against the guide, test edge cases, find bugs, propose tests and fixes |
| Report | Claude Code (Opus 5.5), with my own `/memoria-sin-ia` command | Draft sections 1 to 4 of this report from the facts in the repository, in plain English |
| Bonus: greeting history | Claude Code (Opus 5.5), with `/practica` and `/memoria-sin-ia` | For each block, options with trade-offs and a recommendation; code and tests after my approval; checks run in my terminal; draft of the bonus sections of this report |
| Bonus: live updates (SSE) | Claude Code (Opus 5.5), with `/practica` and `/memoria-sin-ia` | For each block, options with trade-offs and a recommendation; code, tests and the page script after my approval; checks run in my terminal; draft of the SSE sections of this report |
| Bonus: connection pool | Claude Code (Opus 5.5), with `/practica` and `/memoria-sin-ia` | Check that HikariCP was already in use, options for the teacher's recommendation, the pool settings, a concurrency test and the report text |

### Piece 1: Locale-aware greeting

| Field | Entry |
|---|---|
| Representative prompts | I described what I wanted: the greeting in English and Spanish, chosen from the browser language, with a way to change it and remember it. I asked the assistant to work in this way: "Quiero que primero plantees el problema, propongas posibles soluciones, yo elija una y después me propongas los bloques de código que hay que cambiar en cada fichero" (first state the problem, propose possible solutions, I choose one, and then propose the code blocks to change in each file). Then a "sí" (yes) for each block |
| Affected files/sections | `WebConfig.kt`, `HelloController.kt`, `messages*.properties`, `application.properties`, the three test classes |
| Validation steps | `./gradlew check` and `curl` against the running app. Running the code showed two bugs (fixed default locale, `fallback-to-system-locale`) that I fixed before the commit |
| Citations | None |
| Human-reviewed | I chose Spring's `CookieLocaleResolver`, which keeps the language for 30 days. I rejected `SessionLocaleResolver`, which forgets it when the browser closes, and reading `Accept-Language` by hand. I also decided to localize `/api/hello`, which was not required. I approved each file |

### Piece 2: Name validation

| Field | Entry |
|---|---|
| Representative prompts | I described the problem: the `name` parameter accepted any string of any length, and `spring-boot-starter-validation` was in the project but not used. I asked for ways to limit the length and to answer with a clear error. The assistant proposed three options. I answered: "Elijo la B" (I choose B) [B: `@Size` on the `name` parameter, with `@Validated` on the controller]. Then "Sí, aplícalo" (yes, apply it) for each block |
| Affected files/sections | `HelloController.kt`, `ValidationExceptionHandler.kt`, `messages*.properties`, `welcome.html`, `HelloControllerMVCTests.kt` |
| Validation steps | `./gradlew clean check` (17 tests at that point) and `curl` with 50 and 51 characters, on the page and on the API |
| Citations | None |
| Human-reviewed | I chose `@Size` on the parameter and rejected the other two options: a DTO with `@Valid`, which is too much for one field, and a manual `if`, which does not use Bean Validation. I also approved taking the error text from `MessageSource` instead of Bean Validation's own message, so that the error follows the visitor's language |

### Piece 3: Remember the last valid name

| Field | Entry |
|---|---|
| Representative prompts | I described the problem: the language was remembered between visits, but the name was not, and I asked how to keep the last valid name. I answered: "Sí, elijo la B, solo la página" (yes, I choose B, only the page) [B: the cookie is used only by the page `/`, not by `/api/hello`]. When the new signature of `welcome()` broke two unit tests, the assistant proposed ways to handle them, and I answered: "La opción 2 sí es la correcta" (option 2 is the right one) [option 2: remove the two tests, because the MVC tests already covered the same cases] |
| Affected files/sections | `HelloController.kt`, `HelloControllerMVCTests.kt`, `HelloControllerUnitTests.kt` |
| Validation steps | `./gradlew clean check` (18 tests) and `curl` with and without the `name` cookie |
| Citations | None |
| Human-reviewed | I chose to read and write the cookie in the controller, not with an interceptor like the one for the language. I limited the cookie to the page, so that an API call always gives the same answer to the same request. For the broken tests, I rejected rewriting them with a mock response (`MockHttpServletResponse`), because I did not want two tests for the same thing |

### Documentation

| Field | Entry |
|---|---|
| Representative prompts | Not recorded. The drafts were the KDoc of the changed classes and the short note in `README.md` |
| Affected files/sections | KDoc in `HelloController.kt` and `ValidationExceptionHandler.kt`; the `My increment` section of `README.md` |
| Validation steps | `./gradlew check`; I read the KDoc against the code |
| Citations | None |
| Human-reviewed | I approved the texts and kept the README change to one short section |

### Review fixes

| Field | Entry |
|---|---|
| Representative prompts | The assistant checked the increment against the guide and tested it with `curl` and unusual input. When it showed that `/api/hello` in Spanish answered "¡Hola, World!", I wrote: "Quiero que analices esto a fondo: tendría que ser Hello World y Hola Mundo" (analyse this in depth; it should be Hello World and Hola Mundo). After the list of bugs: "Ok a los tres, empieza por el fallo 1" (ok to the three, start with bug 1) |
| Affected files/sections | `WebConfig.kt`, `HelloController.kt`, `application.properties`, `messages*.properties`, `IntegrationTest.kt`, `HelloControllerMVCTests.kt` (commits `8ad4a0a` to `ec08b34`) |
| Validation steps | For each fix, a test written first and seen failing (see *How I verified*); then `./gradlew check` with a Spanish and an English JVM (26 tests) and `curl` against the real server |
| Citations | None. Spring APIs were checked in the Spring 7 jars (`javap`) and by running the app |
| Human-reviewed | Without an `Accept-Language` header, I chose English on any machine, and rejected leaving it as it was and only documenting it. I asked for "World" / "Mundo". I approved each fix and each commit. The assistant's first plan for the cookie (`URLEncoder` + `URLDecoder`) was dropped when a test showed that it would decode the name twice |

### Report

| Field | Entry |
|---|---|
| Representative prompts | "Ok, adelante con What I changed" (ok, go on with What I changed); "Ok, verifica ese dato y monta el REPORT" (ok, check that fact and put the report together). On 25 September, to rewrite this disclosure: "Quiero explicar de forma general la forma de trabajo: te planteo el problema con detalle, te pido soluciones, las analizas y me dices cuáles hay, yo decido cuál elegir, y vamos poco a poco, con mi ok o con mis correcciones" (I want to explain the way of working in general: I describe the problem in detail, I ask you for solutions, you analyse them and tell me the options, I decide which one to choose, and we go step by step, with my ok or my corrections) |
| Affected files/sections | `REPORT.md`, sections *What I specified*, *What I changed*, *Technical decisions*, *How I verified* and this disclosure (written on 23 September, rewritten on 25 September) |
| Validation steps | Every command in the report was run as written. Two facts about Piece 1 were checked again by running the app |
| Citations | None |
| Human-reviewed | I approved each section. Two sentences in the drafts were removed because they were not true. I asked to remove a separate provenance note, and on 25 September I asked to rewrite this disclosure so that every choice says what was chosen and what was rejected |

### Bonus: greeting history

| Field | Entry |
|---|---|
| Representative prompts | "Desarrolla este punto y vuelve a proponérmelo" (develop this point and propose it again) [which language to store: I chose the language the visitor asked for, without the region; I rejected the full tag (`es-ES`) and the language of the answer]; "¿Qué quieres decir con «the 10 most recent ones»? ¿Que la base de datos se sobrescribe?" (what do you mean by "the 10 most recent ones", is the database overwritten?) [no: every greeting is stored, and only the reading is limited to 10; I asked to reword the commit message]; "Podría ser un error 404, analiza esto" (maybe it should be a 404 error, analyse this) [my own idea, a 404 with a custom page; I dropped it after the analysis: `/` exists, and a custom 404 page is UI only and outside what the teacher accepted]; "Antes de seguir, explícame exactamente qué devuelve, con un ejemplo claro de una secuencia real" (before going on, explain exactly what it returns, with a clear example of a real sequence) |
| Affected files/sections | Package `history` (5 classes), `HelloController.kt`, `welcome.html`, `messages*.properties`, `application.properties`, `build.gradle.kts`, `libs.versions.toml`, `.gitignore`, the test classes, `README.md` and the bonus sections of this report (commits `2118a76` to the one that adds this table, on the branch `feature/greeting-history`) |
| Validation steps | `./gradlew clean check` after each block (26, 28, 36, 40 and 46 tests), with a Spanish and an English JVM; each rule broken on purpose to see its test fail; `curl` against the real server, including a restart; the commands in *How to run and test the bonus* run as written |
| Citations | None. Spring and Thymeleaf APIs were checked in the jars of the versions used (for example the package of `@DataJpaTest` in Boot 4.1 and `#temporals.format`) |
| Human-reviewed | I chose every design decision from the options, 15 in total. The main ones: H2 in a file with JPA (rejected: `schema.sql`, `create-drop`); only a name sent in the request is stored (not the cookie name); `/api/greetings` returns a plain array without the id (rejected: exposing the entity, `?limit=`, a wrapper object); an error, not `[]`, if the database fails; the list rendered by the server (rejected: JavaScript). The assistant wrote the code and the tests after I approved each block |

### Bonus: live updates (SSE)

| Field | Entry |
|---|---|
| Representative prompts | I described the teacher's request: show the stored greetings live on the home page with SSE. When the end-to-end test timed out: "A: sendAsync en el test" (A: sendAsync in the test) [change only the test; rejected: making the endpoint send an empty first event]. When the live time and the stored time did not match: "A: truncar a microsegundos" (A: cut to microseconds) [rejected: reading the greeting again after `save()`, or only documenting it]. For the newest id on the page: "El id en GreetingView con @JsonIgnore" (the id in GreetingView, with @JsonIgnore) [rejected: a separate query for the last id, and a second view class]. Then "Ok, adelante" (ok, go ahead) and "Ok, commit" for each block |
| Affected files/sections | `GreetingStream.kt`, `GreetingHistoryController.kt`, `GreetingRepository.kt`, `GreetingHistory.kt`, `Greeting.kt`, `GreetingView.kt`, `HelloController.kt`, `welcome.html`, `static/js/greeting-stream.js`, their test classes, `README.md` and the live-update sections of this report (commits `5f8162f` to `8149d86`, on the branch `feature/greeting-stream`) |
| Validation steps | `./gradlew check` after each block (49, 57, 60, 61 and 66 tests), with a Spanish and an English JVM; each rule broken on purpose to see its test fail (see *How I verified*); `curl -N` against the real server, including `Last-Event-ID`; `node --check` for the script; the two-tab test in the browser, done by me |
| Citations | None. Two Spring and H2 details (the headers of a stream are sent with the first event; H2 stores microseconds) were found by running the code |
| Human-reviewed | I chose each of the 10 design decisions from the options: publish only after `save()`, the database id as event id with `Last-Event-ID`, a separate URL, at most 10 missed greetings, a 30-minute timeout, the error on a database failure, and the four above. The assistant first assumed that the stream sent its headers when it opened; the test showed that this was wrong, and it stopped and asked. The assistant wrote the code, the tests and the page script after I approved each block |

### Bonus: connection pool

| Field | Entry |
|---|---|
| Representative prompts | "El profesor me recomienda usar HikariCP para las conexiones concurrentes" (the teacher recommends HikariCP for concurrent connections). After the options: "Verificar primero" (check first), then "Explícito + test" (explicit + test) [set the pool in `application.properties` and add a concurrency test; rejected: only explaining it in the report, and only the configuration without a test] |
| Affected files/sections | `application.properties`, `IntegrationTest.kt` and the connection-pool sections of this report (commits `b8c2ece` and `cf25f4b`) |
| Validation steps | The dependency tree and the log (`HikariPool-1`, then `greetings-pool`); property names and default values checked in the Spring Boot 4.1 and HikariCP 7.0.2 jars; `./gradlew check`, 67 tests with a Spanish and an English JVM; a pool of 1 with a 250 ms timeout, which did not make the test fail (see *How I verified*) |
| Citations | None |
| Human-reviewed | I kept the default size (10) and did not change the timeouts without a load test. I put the change on the same branch as the live updates instead of a new one. Before the experiment, the assistant said that a pool of 1 would probably not make the test fail, and I decided to report the result as it was |
