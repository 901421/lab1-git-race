package es.unizar.webeng.hello.controller

import es.unizar.webeng.hello.history.GreetingHistory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

class HelloControllerUnitTests {

    private fun testMessageSource(): StaticMessageSource =
        StaticMessageSource().apply {
            addMessage("greeting.named", Locale.ENGLISH, "Hello, {0}!")
        }

    // LocaleContextHolder falls back to the JVM's own default locale when
    // there is no web request (as here). That default depends on the
    // machine running the test (e.g. es_ES on this one), so it's pinned
    // explicitly to keep the test deterministic everywhere.
    @BeforeEach
    fun setup() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
    }

    @AfterEach
    fun tearDown() {
        LocaleContextHolder.resetLocaleContext()
    }

    // welcome() now needs a real HttpServletResponse to write the "remember
    // the name" cookie (Piece 3), so its default/personalized-message cases
    // are no longer meaningfully unit-testable in isolation — they're
    // covered instead by HelloControllerMVCTests, through a real Spring
    // request/response (which can also verify the cookie itself).

    @Test
    fun `should return API response with timestamp`() {
        val apiController = HelloApiController(testMessageSource(), mock(GreetingHistory::class.java))
        val response = apiController.helloApi("Test")

        assertThat(response).containsKey("message")
        assertThat(response).containsKey("timestamp")
        assertThat(response["message"]).isEqualTo("Hello, Test!")
        assertThat(response["timestamp"]).isNotNull()
    }
}
