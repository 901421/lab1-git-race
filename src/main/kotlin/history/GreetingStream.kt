package es.unizar.webeng.hello.history

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the open live connections to the greeting history and sends each new
 * greeting to all of them as a Server-Sent Event named `greeting`.
 *
 * The event id is the database id, so a browser that reconnects can say which
 * greeting it saw last (`Last-Event-ID`). A connection is forgotten when it
 * completes, times out, fails, or a send to it fails, so closed tabs do not pile up.
 */
@Component
class GreetingStream {

    companion object {
        /** After this time the connection ends and the browser reconnects on its own. */
        val TIMEOUT: Duration = Duration.ofMinutes(30)
    }

    private val log = LoggerFactory.getLogger(GreetingStream::class.java)
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    /** Opens a new live connection with the [TIMEOUT]. */
    fun open(): SseEmitter = register(SseEmitter(TIMEOUT.toMillis()))

    /** Keeps [emitter] until it completes, times out or fails. */
    internal fun register(emitter: SseEmitter): SseEmitter {
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitter.complete() }
        emitter.onError { emitters.remove(emitter) }
        emitters.add(emitter)
        return emitter
    }

    /** Forgets [emitter] without sending anything, e.g. when opening it failed. */
    internal fun forget(emitter: SseEmitter) {
        emitters.remove(emitter)
    }

    /** Sends a stored [greeting] to every open connection. */
    fun publish(greeting: Greeting) {
        emitters.forEach { send(it, greeting) }
    }

    /** Sends a stored [greeting] to one connection only, e.g. one it missed. */
    fun send(emitter: SseEmitter, greeting: Greeting) {
        val event = SseEmitter.event()
            .name("greeting")
            .id(checkNotNull(greeting.id) { "Only stored greetings can be sent" }.toString())
            .data(GreetingView(greeting.name, greeting.locale, greeting.createdAt), MediaType.APPLICATION_JSON)
        try {
            emitter.send(event)
        } catch (e: IOException) {
            // The browser went away; Spring completes the emitter, we only forget it
            log.debug("Dropping a closed greeting stream", e)
            emitters.remove(emitter)
        } catch (e: IllegalStateException) {
            // The emitter was already completed (e.g. timed out) before it was removed
            emitters.remove(emitter)
        }
    }

    /** Number of open connections. */
    fun connections(): Int = emitters.size
}
