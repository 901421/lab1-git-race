# Lab 1 Git Race -- Project Report

This note uses the same disclosure fields as the group-project **AI use (10%)** slice. Lab 1 is still **limited**: assistive GenAI only — not a full or substantial generated solution. The project will later expect agents plus `AGENTS.md` and one skill; you do **not** need those here.

Do not invent a percentage of “AI vs original” lines. Empty or fake disclosure fails this lab.

## What I specified

Goal decided before writing any code: make the greeting respond to the visitor's language (English and Spanish, at minimum), inferred from `Accept-Language`, with an explicit `?lang=` override, and persisted across visits (not just the current request). Success criteria: `/` returns the matching text for `en`/`es` headers; `?lang=es` switches the language and it stays switched on the next request without repeating the parameter; `/api/hello` behaves consistently with the page.

## What I changed

- New `src/main/kotlin/config/WebConfig.kt`: registers a `CookieLocaleResolver` (cookie `lang`, 30-day max age) and a `LocaleChangeInterceptor` bound to the `lang` query parameter.
- New `src/main/resources/messages.properties` / `messages_es.properties`: externalized greeting text, replacing the hardcoded `app.message` string.
- Modified `src/main/kotlin/controller/HelloController.kt`: `HelloController` and `HelloApiController` resolve the greeting through `MessageSource` + `LocaleContextHolder` instead of a fixed `@Value` string.
- Modified `src/main/resources/application.properties`: removed the now-unused `app.message`, added `spring.messages.fallback-to-system-locale=false`.
- Removed `src/main/resources/META-INF/additional-spring-configuration-metadata.json`: only documented `app.message`, which no longer exists.
- Modified `IntegrationTest.kt`, `HelloControllerMVCTests.kt`, `HelloControllerUnitTests.kt`: explicit locale in requests/assertions, plus new cases for the Spanish path and the `?lang=` override.

## Technical decisions

- Spring's built-in i18n (`MessageSource` + `LocaleResolver`) over hand-parsing `Accept-Language`: keeps text out of the code, idiomatic.
- `CookieLocaleResolver` over session-based: the requirement was persistence across visits, which a session does not give.
- Localized both the page and `/api/hello`, for consistency, though only the page was strictly required.
- No fixed default locale on the resolver, on purpose: without a cookie/`?lang=`, resolution still falls through to `Accept-Language`.
- `spring.messages.fallback-to-system-locale=false`: an unmatched locale falls back to the base `messages.properties` (English), not the server machine's own OS locale.

## How I verified

- `./gradlew check` → BUILD SUCCESSFUL, all tests passing (2 new: Spanish greeting, `?lang=` override with cookie assertion).
- Manual `curl` checks against the running app: `Accept-Language: en`/`es`, `?lang=es` override + resulting `Set-Cookie`, `/api/hello`.
- Two real bugs found and fixed by running the code (not assumed): (1) a fixed default locale on the resolver made it ignore `Accept-Language` entirely; (2) Spring Boot's default `fallback-to-system-locale=true` let the **server's OS locale** win over the client's requested one.
- Also found MockMvc's `.header("Accept-Language", …)` does not affect the resolved locale in `@WebMvcTest` — `.locale(Locale...)` on the request builder is required instead.

## AI disclosure

- **Tools:** Claude Code (Sonnet 5).
- **Purpose:** assisted implementing this increment through a guided flow: stating the problem, proposing several general solutions with trade-offs, then proposing each file's content one at a time for approval before writing.
- **Representative prompts:** "propusieras el problema primero, propusieras posibles soluciones y elijo una... y me propones bloques de código a modificar para cada fichero"; per-file "sí" / "aplica este bloque" confirmations.
- **Affected files/sections:** see "What I changed" above.
- **Validation steps:** `./gradlew check` and manual `curl` checks, run and read by me.
- **Human-reviewed:** I picked the cookie-based resolver over the two alternatives presented, decided to also localize `/api/hello`, and approved each file individually before it was written.
- **Provenance note:** this exact increment had already been implemented and verified in an earlier session; a local git/repository incident (fork identity, unrelated to this code) required rebuilding the repository from scratch. The assistant kept a private reference of the previous diff to avoid re-deriving trivial syntax, but the problem, the alternatives and the decision were walked through again with me before any file was written, not reapplied blindly.
