package es.unizar.webeng.hello.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.util.Locale

class GreetingHistoryTests {

    private val repository = mock(GreetingRepository::class.java)
    private val history = GreetingHistory(repository)

    @Test
    fun `should store the name with the language but not the region`() {
        // Like the real save(), return the entity; an unstubbed mock returns null
        `when`(repository.save(any(Greeting::class.java))).thenAnswer { it.getArgument<Greeting>(0) }

        history.record("Ana", Locale.forLanguageTag("es-ES"))

        val saved = ArgumentCaptor.forClass(Greeting::class.java)
        verify(repository).save(saved.capture())
        assertThat(saved.value.name).isEqualTo("Ana")
        assertThat(saved.value.locale).isEqualTo("es")
    }

    @Test
    fun `should not fail the greeting when the database is down`() {
        `when`(repository.save(any(Greeting::class.java)))
            .thenThrow(DataAccessResourceFailureException("database is down"))

        assertThatCode { history.record("Ana", Locale.ENGLISH) }.doesNotThrowAnyException()
    }

    @Test
    fun `should return the latest greetings without internal fields`() {
        val createdAt = Instant.parse("2026-09-24T10:00:00Z")
        `when`(repository.findTop10ByOrderByCreatedAtDescIdDesc())
            .thenReturn(listOf(Greeting("Ana", "es", createdAt, id = 7)))

        assertThat(history.latest()).containsExactly(GreetingView("Ana", "es", createdAt))
    }

    @Test
    fun `should return the missed greetings oldest first`() {
        val createdAt = Instant.parse("2026-09-24T10:00:00Z")
        `when`(repository.findTop10ByIdGreaterThanOrderByIdDesc(5)).thenReturn(
            listOf(Greeting("Luis", "fr", createdAt, id = 7), Greeting("Ana", "es", createdAt, id = 6))
        )

        assertThat(history.since(5).map { it.id }).containsExactly(6L, 7L)
    }
}
