package es.unizar.webeng.hello

import es.unizar.webeng.hello.history.GreetingRepository
import es.unizar.webeng.hello.history.GreetingStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var greetingRepository: GreetingRepository

    @Autowired
    private lateinit var greetingStream: GreetingStream

    @Test
    fun `should return home page with modern title and client-side HTTP debug`() {
        val response = restTemplate.getForEntity("http://localhost:$port", String::class.java)
        
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("<title>Modern Web App</title>")
        assertThat(response.body).contains("Welcome to Modern Web App")
        assertThat(response.body).contains("Interactive HTTP Testing & Debug")
        assertThat(response.body).contains("Client-Side Educational Tool")
    }

    private fun withLanguage(tag: String): HttpEntity<Void> =
        HttpEntity(null, HttpHeaders().apply { set("Accept-Language", tag) })

    @Test
    fun `should return personalized greeting when name is provided`() {
        val response = restTemplate.exchange(
            "http://localhost:$port?name=Developer",
            HttpMethod.GET,
            withLanguage("en"),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Hello, Developer!")
    }

    @Test
    fun `should return personalized greeting in Spanish when Accept-Language is es`() {
        val response = restTemplate.exchange(
            "http://localhost:$port?name=Ana",
            HttpMethod.GET,
            withLanguage("es"),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("¡Hola, Ana!")
    }

    @Test
    fun `should return API response with timestamp`() {
        val response = restTemplate.exchange(
            "http://localhost:$port/api/hello?name=Test",
            HttpMethod.GET,
            withLanguage("en"),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(response.body).contains("Hello, Test!")
        assertThat(response.body).contains("timestamp")
    }

    // No Accept-Language header on purpose: the answer must be English on any
    // machine, not the language of the JVM that happens to run the server.
    @Test
    fun `should answer in English when the request has no Accept-Language header`() {
        val response = restTemplate.getForEntity("http://localhost:$port/api/hello?name=Test", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Hello, Test!")
    }

    @Test
    fun `should serve Bootstrap CSS correctly`() {
        val response = restTemplate.getForEntity("http://localhost:$port/webjars/bootstrap/5.3.8/css/bootstrap.min.css", String::class.java)
        
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("body")
        assertThat(response.headers.contentType).isEqualTo(MediaType.valueOf("text/css"))
    }

    @Test
    fun `should expose actuator health endpoint`() {
        val response = restTemplate.getForEntity("http://localhost:$port/actuator/health", String::class.java)
        
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("UP")
    }
    
    @Test
    fun `should display client-side HTTP debug interface`() {
        val response = restTemplate.getForEntity("http://localhost:$port?name=Student", String::class.java)
        
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Interactive HTTP Testing & Debug")
        assertThat(response.body).contains("Client-Side Educational Tool")
        assertThat(response.body).contains("Web Page Greeting")
        assertThat(response.body).contains("API Endpoint")
        assertThat(response.body).contains("Health Check")
        assertThat(response.body).contains("Learning Notes:")
    }

    @Test
    fun `should store a named greeting in the database`() {
        val response = restTemplate.exchange(
            "http://localhost:$port/api/hello?name=Ana", HttpMethod.GET, withLanguage("es-ES"), String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val latest = greetingRepository.findTop10ByOrderByCreatedAtDescIdDesc().first()
        assertThat(latest.name).isEqualTo("Ana")
        assertThat(latest.locale).isEqualTo("es")
    }

    @Test
    fun `should list a stored greeting at api greetings`() {
        restTemplate.exchange(
            "http://localhost:$port/api/hello?name=Ana", HttpMethod.GET, withLanguage("es-ES"), String::class.java
        )

        val response = restTemplate.getForEntity("http://localhost:$port/api/greetings", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(response.body).startsWith("""[{"name":"Ana","locale":"es","timestamp":""")
    }

    @Test
    fun `should show a stored greeting in the history on the page`() {
        val response = restTemplate.getForEntity("http://localhost:$port/?name=Ana", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Recent greetings")
        assertThat(response.body).contains("<strong>Ana</strong>")
    }

    @Test
    fun `should push a new greeting to an open stream`() {
        val before = greetingStream.connections()
        // sendAsync: Spring sends the headers together with the first event,
        // so a blocking send() would wait for a greeting that is never asked for
        val response = HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/greetings/stream")).build(),
            HttpResponse.BodyHandlers.ofLines()
        )
        // Wait until the server keeps the connection, or the greeting would be missed
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (greetingStream.connections() == before) {
            check(System.nanoTime() < deadline) { "The stream was not opened" }
            Thread.sleep(10)
        }

        restTemplate.getForEntity("http://localhost:$port/api/hello?name=Eva", String::class.java)

        val lines = response.get(5, TimeUnit.SECONDS).body()
        // Read in the background: the stream never ends on its own
        val event = CompletableFuture.supplyAsync {
            lines.filter { it.startsWith("data:") }.findFirst().orElseThrow()
        }
        assertThat(event.get(5, TimeUnit.SECONDS)).contains("\"name\":\"Eva\"")
        lines.close()
    }

    @Test
    fun `should serve the live history script`() {
        val response = restTemplate.getForEntity("http://localhost:$port/js/greeting-stream.js", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("EventSource")
    }

    // More requests at the same time than connections in the pool (10):
    // each request must get a connection, store its greeting and give it back
    @Test
    fun `should store every greeting when many requests arrive at the same time`() {
        val prefix = "Pool-${System.nanoTime()}-"
        val requests = 20
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(requests)
        try {
            val statuses = (1..requests).map { i ->
                executor.submit(Callable {
                    start.await()
                    restTemplate.getForEntity(
                        "http://localhost:$port/api/hello?name=$prefix$i", String::class.java
                    ).statusCode.value()
                })
            }
            start.countDown()
            assertThat(statuses.map { it.get(10, TimeUnit.SECONDS) }).containsOnly(200)
        } finally {
            executor.shutdownNow()
        }

        val stored = greetingRepository.findAll().map { it.name }.filter { it.startsWith(prefix) }
        assertThat(stored).hasSize(requests).doesNotHaveDuplicates()
    }
}
