package es.unizar.webeng.hello.history

import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** JSON access to the greeting history, at `/api/greetings`, and its live stream. */
@RestController
class GreetingHistoryController(
    private val greetingHistory: GreetingHistory,
    private val greetingStream: GreetingStream
) {

    /** Returns the 10 most recent greetings, newest first, as a JSON array. */
    @GetMapping("/api/greetings", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun greetings(): List<GreetingView> = greetingHistory.latest()

    /**
     * Opens a Server-Sent Events stream of new greetings, at `/api/greetings/stream`.
     *
     * With [after] (the last id the page shows) or [lastEventId] (sent by the
     * browser when it reconnects, and preferred), the greetings stored after
     * that id are sent first, at most 10. Without either, only new ones are sent.
     * If the database cannot be read, the request fails like `/api/greetings`.
     */
    @GetMapping("/api/greetings/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @RequestParam(required = false) after: Long?,
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventId: Long?
    ): SseEmitter {
        // Registered before reading the database, so a greeting stored in between
        // is not lost; if it is sent twice, the page drops it by its id
        val emitter = greetingStream.open()
        val missed = try {
            (lastEventId ?: after)?.let { greetingHistory.since(it) } ?: emptyList()
        } catch (e: DataAccessException) {
            greetingStream.forget(emitter)
            throw e
        }
        missed.forEach { greetingStream.send(emitter, it) }
        return emitter
    }
}
