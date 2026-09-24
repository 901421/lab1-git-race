package es.unizar.webeng.hello.history

import java.time.Instant

/** A stored greeting as the API returns it, without internal fields such as the id. */
data class GreetingView(val name: String, val locale: String, val timestamp: Instant)
