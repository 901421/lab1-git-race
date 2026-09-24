package es.unizar.webeng.hello.history

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Records greetings in the history and pushes each stored one to the live
 * stream. A database error is logged and ignored, so the history can never
 * break the greeting itself.
 */
@Service
class GreetingHistory(
    private val repository: GreetingRepository,
    private val stream: GreetingStream
) {

    private val log = LoggerFactory.getLogger(GreetingHistory::class.java)

    /**
     * Stores [name] with the language the visitor asked for (e.g. `es`, without
     * region) and, only if that worked, sends it to the open live connections.
     */
    fun record(name: String, locale: Locale) {
        val saved = try {
            repository.save(Greeting(name, locale.language))
        } catch (e: DataAccessException) {
            log.warn("Could not store the greeting in the history", e)
            return
        }
        // Published after save(), so the live view never shows what the database lacks
        stream.publish(saved)
    }

    /** The 10 most recent greetings, newest first. A database error is not caught here. */
    fun latest(): List<GreetingView> =
        repository.findTop10ByOrderByCreatedAtDescIdDesc()
            .map { it.toView() }

    /**
     * Up to 10 greetings stored after [id], oldest first, so they can be sent
     * in order. If more were missed, only the 10 most recent are returned.
     * A database error is not caught here.
     */
    fun since(id: Long): List<Greeting> =
        repository.findTop10ByIdGreaterThanOrderByIdDesc(id).reversed()
}
