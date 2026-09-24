package es.unizar.webeng.hello.history

import es.unizar.webeng.hello.controller.HelloController
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.time.Instant

/**
 * One greeting shown to a visitor, stored so the history survives a restart.
 *
 * Only the name and the language are kept: the greeting text itself can be
 * rebuilt from `messages*.properties`, so storing it would duplicate data.
 */
@Entity
class Greeting(
    @Column(nullable = false, length = HelloController.MAX_NAME_LENGTH)
    val name: String,
    /** Language tag of the response, e.g. `en` or `es`. */
    @Column(nullable = false, length = 35)
    val locale: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Id @GeneratedValue
    val id: Long? = null
)
