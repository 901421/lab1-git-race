package es.unizar.webeng.hello.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.ui.Model
import org.springframework.ui.ExtendedModelMap
import java.util.Locale

class HelloControllerUnitTests {
    private lateinit var controller: HelloController
    private lateinit var model: Model

    private fun testMessageSource(): StaticMessageSource =
        StaticMessageSource().apply {
            addMessage("greeting.default", Locale.ENGLISH, "Test Message")
            addMessage("greeting.named", Locale.ENGLISH, "Hello, {0}!")
        }

    // LocaleContextHolder falls back to the JVM's own default locale when
    // there is no web request (as here). That default depends on the
    // machine running the test (e.g. es_ES on this one), so it's pinned
    // explicitly to keep the test deterministic everywhere.
    @BeforeEach
    fun setup() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        controller = HelloController(testMessageSource())
        model = ExtendedModelMap()
    }

    @AfterEach
    fun tearDown() {
        LocaleContextHolder.resetLocaleContext()
    }

    @Test
    fun `should return welcome view with default message`() {
        val view = controller.welcome(model, "")

        assertThat(view).isEqualTo("welcome")
        assertThat(model.getAttribute("message")).isEqualTo("Test Message")
        assertThat(model.getAttribute("name")).isEqualTo("")
    }

    @Test
    fun `should return welcome view with personalized message`() {
        val view = controller.welcome(model, "Developer")

        assertThat(view).isEqualTo("welcome")
        assertThat(model.getAttribute("message")).isEqualTo("Hello, Developer!")
        assertThat(model.getAttribute("name")).isEqualTo("Developer")
    }

    @Test
    fun `should return API response with timestamp`() {
        val apiController = HelloApiController(testMessageSource())
        val response = apiController.helloApi("Test")

        assertThat(response).containsKey("message")
        assertThat(response).containsKey("timestamp")
        assertThat(response["message"]).isEqualTo("Hello, Test!")
        assertThat(response["timestamp"]).isNotNull()
    }
}
