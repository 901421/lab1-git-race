package es.unizar.webeng.hello.controller

import es.unizar.webeng.hello.config.WebConfig
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
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
    @Value("\${app.message:Welcome to the Modern Web App!}")
    private lateinit var message: String

    @Autowired
    private lateinit var mockMvc: MockMvc

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
    fun `should return API response as JSON in English`() {
        mockMvc.perform(get("/api/hello").param("name", "Test").locale(Locale.ENGLISH))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message", equalTo("Hello, Test!")))
            .andExpect(jsonPath("$.timestamp").exists())
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
}
