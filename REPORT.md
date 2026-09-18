# Lab 1 Git Race -- Project Report

This note uses the same disclosure fields as the group-project **AI use (10%)** slice. Lab 1 is still **limited**: assistive GenAI only — not a full or substantial generated solution. The project will later expect agents plus `AGENTS.md` and one skill; you do **not** need those here.

Do not invent a percentage of “AI vs original” lines. Empty or fake disclosure fails this lab.

## What I specified

### Piece 1 — Locale-aware greeting

Goal decided before writing any code: make the greeting respond to the visitor's language (English and Spanish, at minimum), inferred from `Accept-Language`, with an explicit `?lang=` override, and persisted across visits (not just the current request). Success criteria: `/` returns the matching text for `en`/`es` headers; `?lang=es` switches the language and it stays switched on the next request without repeating the parameter; `/api/hello` behaves consistently with the page.

### Piece 2 — Name validation

Goal decided before writing any code: the `name` parameter currently accepts any string, of any length, with no restriction — including the already-unused `spring-boot-starter-validation` dependency. The increment adds a length limit via Bean Validation, with a controlled response when violated: a friendly inline message on the page (`/`), and a clean JSON 400 on the API (`/api/hello`), instead of a generic failure or an uncontrolled stack trace.

### Piece 3 — Remember the last valid name (optional)

Goal decided before writing any code: unlike the locale (Piece 1), the visitor's name was not remembered across visits — every request without an explicit `?name=` fell back to the generic greeting. This optional increment makes a valid, non-empty name persist across visits via a cookie, the same way the locale already does. Success criteria: a valid `?name=` sets a cookie; a later visit with no `name` param greets using that cookie and pre-fills the existing name field; an explicit empty `?name=` still greets generically without touching the cookie; an invalid (too long) name still fails exactly as in Piece 2.

## What I changed

### Piece 1 — Locale-aware greeting

- New `src/main/kotlin/config/WebConfig.kt`: registers a `CookieLocaleResolver` (cookie `lang`, 30-day max age) and a `LocaleChangeInterceptor` bound to the `lang` query parameter.
- New `src/main/resources/messages.properties` / `messages_es.properties`: externalized greeting text, replacing the hardcoded `app.message` string.
- Modified `src/main/kotlin/controller/HelloController.kt`: `HelloController` and `HelloApiController` resolve the greeting through `MessageSource` + `LocaleContextHolder` instead of a fixed `@Value` string.
- Modified `src/main/resources/application.properties`: removed the now-unused `app.message`, added `spring.messages.fallback-to-system-locale=false`.
- Removed `src/main/resources/META-INF/additional-spring-configuration-metadata.json`: only documented `app.message`, which no longer exists.
- Modified `IntegrationTest.kt`, `HelloControllerMVCTests.kt`, `HelloControllerUnitTests.kt`: explicit locale in requests/assertions, plus new cases for the Spanish path and the `?lang=` override.

### Piece 2 — Name validation

- Modified `HelloController.kt`: `@Validated` on both controller classes, `@Size(max = MAX_NAME_LENGTH)` on the `name` parameter of both endpoints, `MAX_NAME_LENGTH = 50` as a shared constant.
- New `ValidationExceptionHandler.kt`: `@ControllerAdvice` catching `ConstraintViolationException`, returning a `ModelAndView` (page) or a JSON `ResponseEntity` (API) with status 400, chosen by inspecting the request path.
- Modified `messages.properties` / `messages_es.properties`: new `name.tooLong` key.
- Modified `welcome.html`: conditional Bootstrap alert (`th:if="${error}"`) showing the error when present.
- Modified `HelloControllerMVCTests.kt`: two new test cases (page and API) for a name over the limit.

### Piece 3 — Remember the last valid name (optional)

- Modified `HelloController.kt`: `welcome()` now reads `@CookieValue(name = "name", required = false)` and writes the cookie via `ResponseCookie` (30-day max age, mirroring the locale cookie) when a valid non-blank `name` is provided. No changes to `HelloApiController` — scoped to the page only.
- Modified `HelloControllerMVCTests.kt`: three new cases (cookie is set, cookie is used when `name` is absent, explicit empty `name` does not use the cookie).
- Modified `HelloControllerUnitTests.kt`: removed two tests that called `welcome()` directly with its old signature (no longer compiles now that it needs a real `HttpServletResponse`); their coverage was already duplicated, and better exercised, by the MVC-slice tests.

## Technical decisions

### Piece 1 — Locale-aware greeting

- Spring's built-in i18n (`MessageSource` + `LocaleResolver`) over hand-parsing `Accept-Language`: keeps text out of the code, idiomatic.
- `CookieLocaleResolver` over session-based: the requirement was persistence across visits, which a session does not give.
- Localized both the page and `/api/hello`, for consistency, though only the page was strictly required.
- No fixed default locale on the resolver, on purpose: without a cookie/`?lang=`, resolution still falls through to `Accept-Language`.
- `spring.messages.fallback-to-system-locale=false`: an unmatched locale falls back to the base `messages.properties` (English), not the server machine's own OS locale.

### Piece 2 — Name validation

- Constraint annotated directly on the `@RequestParam` (with `@Validated` on the class) instead of a dedicated DTO: the smallest change that genuinely uses Bean Validation, justified for a single validated field.
- Error text is **not** produced by Bean Validation's own message interpolation, which by default resolves against the JVM's locale, not the request's — that would have reintroduced the exact locale bug fixed in Piece 1. Instead, the message is resolved explicitly through the same `MessageSource` + `LocaleContextHolder` path already used for greetings.
- A single `@ExceptionHandler` picks the response shape by checking `request.requestURI` (`/api/` prefix): simplest option with only two fixed routes; content negotiation would be over-engineering here.
- Rejected a full DTO + `@Valid @ModelAttribute` approach (considered in the design discussion): more boilerplate than justified for one field.

### Piece 3 — Remember the last valid name (optional)

- `@CookieValue` + `ResponseCookie` directly in the controller (no dedicated interceptor): the smallest option for a single, optional field; a `WebConfig`-style interceptor (mirroring the locale one) was considered but not justified for this scope.
- An explicit empty `?name=` deliberately does **not** fall back to the cookie (only an *absent* parameter does) — same "explicit override wins" pattern already used for `?lang=` in Piece 1.
- No template changes needed: the existing `webName` input already binds to `${name}`, so it gets pre-filled automatically once the controller resolves the remembered name.
- Scoped to the page only, not `/api/hello`: an API call is normally explicit about `name`, so remembering it has no clear benefit there.

## How I verified

### Piece 1 — Locale-aware greeting

- `./gradlew check` → BUILD SUCCESSFUL, all tests passing (2 new: Spanish greeting, `?lang=` override with cookie assertion).
- Manual `curl` checks against the running app: `Accept-Language: en`/`es`, `?lang=es` override + resulting `Set-Cookie`, `/api/hello`.
- Two real bugs found and fixed by running the code (not assumed): (1) a fixed default locale on the resolver made it ignore `Accept-Language` entirely; (2) Spring Boot's default `fallback-to-system-locale=true` let the **server's OS locale** win over the client's requested one.
- Also found MockMvc's `.header("Accept-Language", …)` does not affect the resolved locale in `@WebMvcTest` — `.locale(Locale...)` on the request builder is required instead.

### Piece 2 — Name validation

- `./gradlew clean check` → BUILD SUCCESSFUL, 17 tests, 0 failures (5 new since Piece 1: 2 locale + this piece's 2 validation cases, plus one pre-existing recount).
- Manual `curl` checks against the running app: name of exactly 50 chars (accepted), 51 chars (rejected) on both the page and the API, in English and Spanish, confirming the correct status code, the alert on the page, and the JSON error body.

### Piece 3 — Remember the last valid name (optional)

- `./gradlew clean check` → BUILD SUCCESSFUL, 18 tests, 0 failures.
- Manual `curl` checks: `?name=Ana` sets `Set-Cookie: name=Ana`; a later request with `-b "name=Ana"` and no `name` param greets "Hello, Ana!" and pre-fills the input; `?name=` with the same cookie present still shows the generic greeting; `/api/hello` unaffected.
- Real issue found while implementing (not assumed): changing `welcome()`'s signature broke `HelloControllerUnitTests` at compile time. Stopped, presented two options (mock `HttpServletResponse` vs. retire the now-redundant tests), and removed them after confirming they duplicated, without adding, coverage already present in the MVC-slice tests.

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
