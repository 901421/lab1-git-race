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
}
