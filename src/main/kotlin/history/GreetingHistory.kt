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

    /**
     * Up to 10 greetings stored after [id], oldest first, so they can be sent
     * in order. If more were missed, only the 10 most recent are returned.
     * A database error is not caught here.
     */
    fun since(id: Long): List<Greeting> =
        repository.findTop10ByIdGreaterThanOrderByIdDesc(id).reversed()
}
