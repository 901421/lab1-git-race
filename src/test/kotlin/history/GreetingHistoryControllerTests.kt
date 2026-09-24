package es.unizar.webeng.hello.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

// The real GreetingStream is used, so the tests see the connections it keeps.
// It lives as long as the Spring context, so counts are compared with "before".
@WebMvcTest(GreetingHistoryController::class)
@Import(GreetingStream::class)
class GreetingHistoryControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var greetingStream: GreetingStream

    @MockitoBean
    private lateinit var greetingHistory: GreetingHistory

    private fun stored(id: Long, name: String) =
        Greeting(name, "es", Instant.parse("2026-09-24T10:00:00Z").plusSeconds(id), id = id)

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

    @Test
    fun `should open an event stream`() {
        val before = greetingStream.connections()

        mockMvc.perform(get("/api/greetings/stream"))
            .andExpect(request().asyncStarted())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))

        assertThat(greetingStream.connections()).isEqualTo(before + 1)
    }

    @Test
    fun `should send the missed greetings after the given id`() {
        `when`(greetingHistory.since(5)).thenReturn(listOf(stored(6, "Ana"), stored(7, "Luis")))

        val body = mockMvc.perform(get("/api/greetings/stream").param("after", "5"))
            .andExpect(request().asyncStarted())
            .andReturn().response.contentAsString

        assertThat(body).containsSubsequence("id:6", "\"name\":\"Ana\"", "id:7", "\"name\":\"Luis\"")
    }

    @Test
    fun `should prefer Last-Event-ID over after`() {
        mockMvc.perform(get("/api/greetings/stream").param("after", "2").header("Last-Event-ID", "7"))
            .andExpect(request().asyncStarted())

        verify(greetingHistory).since(7)
        verify(greetingHistory, never()).since(2)
    }

    @Test
    fun `should send nothing missed without an id`() {
        mockMvc.perform(get("/api/greetings/stream"))
            .andExpect(request().asyncStarted())

        verify(greetingHistory, never()).since(anyLong())
    }

    @Test
    fun `should forget the connection when the browser closes it`() {
        val before = greetingStream.connections()
        val result = mockMvc.perform(get("/api/greetings/stream"))
            .andExpect(request().asyncStarted())
            .andReturn()

        result.request.asyncContext!!.complete()

        assertThat(greetingStream.connections()).isEqualTo(before)
    }

    @Test
    fun `should fail and forget the connection when the database is down`() {
        `when`(greetingHistory.since(5)).thenThrow(DataAccessResourceFailureException("database is down"))
        val before = greetingStream.connections()

        // MockMvc rethrows what the controller throws; a real server answers 500
        assertThatThrownBy { mockMvc.perform(get("/api/greetings/stream").param("after", "5")) }
            .hasCauseInstanceOf(DataAccessResourceFailureException::class.java)
        assertThat(greetingStream.connections()).isEqualTo(before)
    }
}
