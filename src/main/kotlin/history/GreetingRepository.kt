package es.unizar.webeng.hello.history

import org.springframework.data.jpa.repository.JpaRepository

/** Access to the stored greetings. */
interface GreetingRepository : JpaRepository<Greeting, Long> {

    /** The 10 most recent greetings, newest first; ties are broken by id. */
    fun findTop10ByOrderByCreatedAtDescIdDesc(): List<Greeting>

    /** The 10 most recent greetings stored after [id], newest first. */
    fun findTop10ByIdGreaterThanOrderByIdDesc(id: Long): List<Greeting>
}
