package es.unizar.webeng.hello.controller

import es.unizar.webeng.hello.config.WebConfig
import es.unizar.webeng.hello.history.GreetingHistory
import jakarta.servlet.http.Cookie
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.Locale

// WebConfig is imported explicitly: @WebMvcTest only scans controllers by
// default, not plain @Configuration classes, so the custom CookieLocaleResolver
// and its ?lang= interceptor would otherwise not be active in this slice.
@WebMvcTest(HelloController::class, HelloApiController::class)
@Import(WebConfig::class)
class HelloControllerMVCTests {
    private val message = "Welcome to the Modern Web App!"

    @Autowired
    private lateinit var mockMvc: MockMvc

    // The history needs a database, which this web-only slice does not start
    @MockitoBean
    private lateinit var greetingHistory: GreetingHistory

    @Test
    fun `should return home page with default message in English`() {
        mockMvc.perform(get("/").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(view().name("welcome"))
            .andExpect(model().attribute("message", equalTo(message)))
            .andExpect(model().attribute("name", equalTo("")))
    }

    @Test
    fun `should return home page with personalized message in English`() {
        mockMvc.perform(get("/").param("name", "Developer").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(view().name("welcome"))
            .andExpect(model().attribute("message", equalTo("Hello, Developer!")))
            .andExpect(model().attribute("name", equalTo("Developer")))
    }

    @Test
    fun `should return home page in Spanish when browser locale is es`() {
        mockMvc.perform(get("/").param("name", "Ana").locale(Locale.forLanguageTag("es")))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("¡Hola, Ana!")))
    }

    @Test
    fun `should let lang query param override the browser locale`() {
        mockMvc.perform(get("/").param("lang", "es").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("¡Bienvenido a la aplicación web moderna!")))
            .andExpect(cookie().value("lang", "es"))
    }

    @Test
    fun `should use the language remembered in the lang cookie`() {
        mockMvc.perform(get("/").cookie(Cookie("lang", "es")).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("¡Bienvenido a la aplicación web moderna!")))
    }

    // There is no messages_fr.properties, so the base (English) file is used,
    // never the messages of the server's own locale.
    @Test
    fun `should fall back to English for an unsupported language`() {
        mockMvc.perform(get("/").locale(Locale.FRENCH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo(message)))
    }

    @Test
    fun `should ignore an invalid lang query param instead of failing`() {
        mockMvc.perform(get("/").param("lang", ";;").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo(message)))
    }

    @Test
    fun `should return API response as JSON in English`() {
        mockMvc.perform(get("/api/hello").param("name", "Test").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message", equalTo("Hello, Test!")))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `should greet the default name in English when the API gets no name`() {
        mockMvc.perform(get("/api/hello").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message", equalTo("Hello, World!")))
    }

    @Test
    fun `should translate the default name when the API gets no name in Spanish`() {
        mockMvc.perform(get("/api/hello").locale(Locale.forLanguageTag("es")))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message", equalTo("¡Hola, Mundo!")))
    }

    @Test
    fun `should return 400 with friendly error when name exceeds max length on the page`() {
        val tooLong = "a".repeat(51)
        mockMvc.perform(get("/").param("name", tooLong).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(view().name("welcome"))
            .andExpect(model().attribute("error", "Name must be at most 50 characters."))
    }

    @Test
    fun `should return 400 JSON error when API name exceeds max length`() {
        val tooLong = "a".repeat(51)
        mockMvc.perform(get("/api/hello").param("name", tooLong).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", equalTo("Name must be at most 50 characters.")))
    }

    @Test
    fun `should set a cookie with the name when a valid name is provided`() {
        mockMvc.perform(get("/").param("name", "Ana").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(cookie().value("name", "Ana"))
            .andExpect(cookie().maxAge("name", 30 * 24 * 60 * 60))
    }

    // Spaces and non-ASCII letters are not allowed raw in a cookie value, so
    // the name is stored URL-encoded; @CookieValue decodes it when read back.
    @Test
    fun `should remember a name with spaces and accents`() {
        mockMvc.perform(get("/").param("name", "José María").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("Hello, José María!")))
            .andExpect(cookie().value("name", "Jos%C3%A9%20Mar%C3%ADa"))
    }

    @Test
    fun `should decode a remembered name with spaces and accents`() {
        mockMvc.perform(get("/").cookie(Cookie("name", "Jos%C3%A9%20Mar%C3%ADa")).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("Hello, José María!")))
            .andExpect(model().attribute("name", equalTo("José María")))
    }

    @Test
    fun `should greet using the remembered name when none is provided in the URL`() {
        mockMvc.perform(get("/").cookie(Cookie("name", "Ana")).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo("Hello, Ana!")))
            .andExpect(model().attribute("name", equalTo("Ana")))
    }

    @Test
    fun `should not use the remembered name when name is explicitly empty`() {
        mockMvc.perform(get("/").param("name", "").cookie(Cookie("name", "Ana")).locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(model().attribute("message", equalTo(message)))
            .andExpect(model().attribute("name", equalTo("")))
    }

    @Test
    fun `should record a name sent to the page`() {
        mockMvc.perform(get("/").param("name", "Ana").locale(Locale.forLanguageTag("es")))
            .andExpect(status().isOk)

        verify(greetingHistory).record("Ana", Locale.forLanguageTag("es"))
    }

    @Test
    fun `should record a name sent to the API`() {
        mockMvc.perform(get("/api/hello").param("name", "Ana").locale(Locale.ENGLISH))
            .andExpect(status().isOk)

        verify(greetingHistory).record("Ana", Locale.ENGLISH)
    }

    @Test
    fun `should not record the name remembered in the cookie`() {
        mockMvc.perform(get("/").cookie(Cookie("name", "Ana")).locale(Locale.ENGLISH))
            .andExpect(status().isOk)

        verifyNoInteractions(greetingHistory)
    }

    @Test
    fun `should not record a missing or empty name`() {
        mockMvc.perform(get("/").locale(Locale.ENGLISH)).andExpect(status().isOk)
        mockMvc.perform(get("/").param("name", "").locale(Locale.ENGLISH)).andExpect(status().isOk)
        mockMvc.perform(get("/api/hello").locale(Locale.ENGLISH)).andExpect(status().isOk)
        mockMvc.perform(get("/api/hello").param("name", "").locale(Locale.ENGLISH)).andExpect(status().isOk)

        verifyNoInteractions(greetingHistory)
    }

    @Test
    fun `should not record a name that is too long`() {
        val tooLong = "a".repeat(HelloController.MAX_NAME_LENGTH + 1)
        mockMvc.perform(get("/").param("name", tooLong).locale(Locale.ENGLISH))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/hello").param("name", tooLong).locale(Locale.ENGLISH))
            .andExpect(status().isBadRequest)

        verifyNoInteractions(greetingHistory)
    }
}
