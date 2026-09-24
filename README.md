# Lab 1 Git Race

Individual starter for Web Engineering 2026–27. Stack matches the group project: **Java 25 LTS**, **Kotlin 2.4.0**, **Spring Boot 4.1.0**, **Gradle 9.6.0**, **Bootstrap 5.3.8**.

The assignment, AI rules, and deadline are in [`docs/GUIDE.md`](docs/GUIDE.md). Fill [`REPORT.md`](REPORT.md) before you submit. Delivery is the Moodle zip only (`docs/GUIDE.md`).

## Run

Java 25 is required (`./gradlew` uses the wrapper). GitHub Codespaces is optional (`docs/GUIDE.md`). Clone this course repository; you do not fork it to submit.

```bash
git clone https://github.com/UNIZAR-30246-WebEngineering/lab1-git-race.git
cd lab1-git-race
./gradlew check
./gradlew bootRun
```

- UI: <http://localhost:8080>
- JSON: <http://localhost:8080/api/hello>
- Health: <http://localhost:8080/actuator/health>

```bash
./gradlew test
./gradlew test --tests "HelloControllerUnitTests"
```

## Layout

```
src/main/kotlin/HelloWorld.kt          # class Application
src/main/kotlin/controller/HelloController.kt
src/main/resources/templates/welcome.html
src/test/kotlin/controller/HelloControllerUnitTests.kt
src/test/kotlin/controller/HelloControllerMVCTests.kt
src/test/kotlin/IntegrationTest.kt
```

## My increment

Locale-aware greeting (English/Spanish, from `Accept-Language` or `?lang=`),
Bean Validation on `name` (max 50 characters, with a friendly error), and
the last valid `name` remembered across visits via a cookie. Full details,
technical decisions, and AI disclosure in [`REPORT.md`](REPORT.md).

```bash
curl -H "Accept-Language: es" "http://localhost:8080/?name=Ana"
curl "http://localhost:8080/?name=$(python3 -c 'print("a"*51)')"   # 400, name too long
```

**Bonus: greeting history.** When a request sends a `name`, the greeting is
stored in an H2 database file (`./data/greetings.mv.db`), so it survives a
restart. The 10 most recent greetings are returned by `GET /api/greetings`
and listed on the home page. Delete the `data/` folder to start with an
empty history.

```bash
curl "http://localhost:8080/api/hello?name=Ana"
curl "http://localhost:8080/api/greetings"   # [{"name":"Ana","locale":"en","timestamp":"..."}]
```

## License

MIT — see `LICENSE`.
