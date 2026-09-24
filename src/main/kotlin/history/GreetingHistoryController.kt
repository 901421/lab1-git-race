package es.unizar.webeng.hello.history

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** JSON access to the greeting history, at `/api/greetings`. */
@RestController
class GreetingHistoryController(private val greetingHistory: GreetingHistory) {

    /** Returns the 10 most recent greetings, newest first, as a JSON array. */
    @GetMapping("/api/greetings", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun greetings(): List<GreetingView> = greetingHistory.latest()
}
