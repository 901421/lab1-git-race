package es.unizar.webeng.hello

import es.unizar.webeng.hello.history.GreetingRepository
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var greetingRepository: GreetingRepository

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
}
