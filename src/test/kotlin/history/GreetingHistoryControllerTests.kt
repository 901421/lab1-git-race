package es.unizar.webeng.hello.history

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(GreetingHistoryController::class)
class GreetingHistoryControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var greetingHistory: GreetingHistory

    @Test
    fun `should return the history as a JSON array, newest first`() {
        `when`(greetingHistory.latest()).thenReturn(
            listOf(
                GreetingView("Luis", "fr", Instant.parse("2026-09-24T10:00:05Z")),
                GreetingView("Ana", "es", Instant.parse("2026-09-24T10:00:00Z"))
            )
        )

        mockMvc.perform(get("/api/greetings"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Luis"))
            .andExpect(jsonPath("$[0].locale").value("fr"))
            .andExpect(jsonPath("$[0].timestamp").value("2026-09-24T10:00:05Z"))
            .andExpect(jsonPath("$[0].id").doesNotExist())
            .andExpect(jsonPath("$[1].name").value("Ana"))
    }

    @Test
    fun `should return an empty array when there is no history`() {
        `when`(greetingHistory.latest()).thenReturn(emptyList())

        mockMvc.perform(get("/api/greetings"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }
}
