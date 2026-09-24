package es.unizar.webeng.hello.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant

@DataJpaTest
class GreetingRepositoryTests {

    @Autowired
    private lateinit var repository: GreetingRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private val base = Instant.parse("2026-09-24T10:00:00Z")

    @Test
    fun `should store a greeting and read it back`() {
        val saved = entityManager.persistAndFlush(Greeting("Ana", "es", base))
        // Clear the persistence context so the next read really hits the database
        entityManager.clear()

        val found = repository.findById(saved.id!!).orElseThrow()

        assertThat(found.name).isEqualTo("Ana")
        assertThat(found.locale).isEqualTo("es")
        assertThat(found.createdAt).isEqualTo(base)
    }

    @Test
    fun `should return the 10 most recent greetings, newest first`() {
        (1..12).forEach { i ->
            entityManager.persist(Greeting("Name$i", "en", base.plusSeconds(i.toLong())))
        }
        entityManager.flush()

        val latest = repository.findTop10ByOrderByCreatedAtDescIdDesc()

        assertThat(latest).hasSize(10)
        assertThat(latest.map { it.name }).containsExactlyElementsOf((12 downTo 3).map { "Name$it" })
    }

    @Test
    fun `should return the 10 most recent greetings after an id`() {
        val stored = (1..12).map { i -> entityManager.persist(Greeting("Name$i", "en", base.plusSeconds(i.toLong()))) }
        entityManager.flush()

        val missed = repository.findTop10ByIdGreaterThanOrderByIdDesc(stored.first().id!!)

        // 11 greetings came after the first one; the oldest of them (Name2) is left out
        assertThat(missed.map { it.name }).containsExactlyElementsOf((12 downTo 3).map { "Name$it" })
    }
}
