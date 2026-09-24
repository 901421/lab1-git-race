package es.unizar.webeng.hello.history

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Records greetings in the history. A database error is logged and ignored,
 * so the history can never break the greeting itself.
 */
@Service
class GreetingHistory(private val repository: GreetingRepository) {

    private val log = LoggerFactory.getLogger(GreetingHistory::class.java)

    /** Stores [name] with the language the visitor asked for (e.g. `es`, without region). */
    fun record(name: String, locale: Locale) {
        try {
            repository.save(Greeting(name, locale.language))
        } catch (e: DataAccessException) {
            log.warn("Could not store the greeting in the history", e)
        }
    }

    /** The 10 most recent greetings, newest first. A database error is not caught here. */
    fun latest(): List<GreetingView> =
        repository.findTop10ByOrderByCreatedAtDescIdDesc()
            .map { GreetingView(it.name, it.locale, it.createdAt) }
}
