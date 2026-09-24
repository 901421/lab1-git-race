package es.unizar.webeng.hello.history

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

/**
 * A stored greeting as the API returns it. The [id] is only used by the home
 * page (`data-id`, and where its live stream starts) and is never sent as JSON.
 */
data class GreetingView(
    val name: String,
    val locale: String,
    val timestamp: Instant,
    @get:JsonIgnore val id: Long
)

/** The view of a stored greeting; it must have an id. */
fun Greeting.toView() =
    GreetingView(name, locale, createdAt, checkNotNull(id) { "Only stored greetings have a view" })
