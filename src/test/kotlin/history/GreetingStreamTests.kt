package es.unizar.webeng.hello.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant

class GreetingStreamTests {

    /** Keeps what would be written to the browser, or fails like a closed tab. */
    private class RecordingEmitter(private val closed: Boolean = false) : SseEmitter() {
        val sent = mutableListOf<String>()

        override fun send(builder: SseEventBuilder) {
            if (closed) throw IOException("browser closed")
            sent += builder.build().joinToString("") { it.data.toString() }
        }
    }

    private val stream = GreetingStream()
    private val greeting = Greeting("Ana", "es", Instant.parse("2026-09-25T10:00:00Z"), id = 7)

    @Test
    fun `should send a stored greeting to every open connection`() {
        val first = RecordingEmitter()
        val second = RecordingEmitter()
        stream.register(first)
        stream.register(second)

        stream.publish(greeting)

        listOf(first, second).forEach {
            assertThat(it.sent).hasSize(1)
            assertThat(it.sent[0]).contains("event:greeting", "id:7")
        }
    }

    @Test
    fun `should forget a connection when sending to it fails`() {
        val open = RecordingEmitter()
        stream.register(RecordingEmitter(closed = true))
        stream.register(open)

        stream.publish(greeting)

        assertThat(stream.connections()).isEqualTo(1)
        assertThat(open.sent).hasSize(1)
    }

    @Test
    fun `should open connections with a 30 minute timeout`() {
        val emitter = stream.open()

        assertThat(emitter.timeout).isEqualTo(GreetingStream.TIMEOUT.toMillis())
        assertThat(stream.connections()).isEqualTo(1)
    }
}
