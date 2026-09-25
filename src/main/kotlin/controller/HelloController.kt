package es.unizar.webeng.hello.controller

import es.unizar.webeng.hello.history.GreetingHistory
import es.unizar.webeng.hello.history.GreetingView
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * Serves the welcome page with a locale-aware, optionally personalized
 * greeting for the "hello-app" increment.
 */
@Controller
@Validated
class HelloController(
    private val messageSource: MessageSource,
    private val greetingHistory: GreetingHistory
) {

    companion object {
        const val MAX_NAME_LENGTH = 50
        private val NAME_COOKIE_MAX_AGE: Duration = Duration.ofDays(30)
    }

    private val log = LoggerFactory.getLogger(HelloController::class.java)

    /**
     * Renders the welcome page with a personalized, locale-aware greeting.
     *
     * Resolution order for the visitor's name: the [name] query parameter if
     * present (even if blank, which forces the generic greeting for this
     * request only); otherwise the value remembered in the `name` cookie, if
     * any. A valid, non-blank [name] is (re)stored in that cookie for 30 days.
     *
     * A non-blank [name] sent in the query string is also recorded in the
     * greeting history. The page then lists the latest greetings. If the
     * history cannot be read, the list is replaced by a short notice and the
     * page still works. Otherwise the page also receives the id where its live
     * stream starts (`lastGreetingId`).
     *
     * @param model Spring MVC model, populated with `message`, `name`,
     *              `history`, `historyUnavailable` and `lastGreetingId`.
     * @param response used to set the `name` cookie when applicable.
     * @param name name from the query string, at most [MAX_NAME_LENGTH] characters.
     * @param rememberedName name remembered from a previous visit, via cookie.
     * @return the `welcome` view name.
     */
    @GetMapping("/")
    fun welcome(
        model: Model,
        response: HttpServletResponse,
        @RequestParam(required = false) @Size(max = MAX_NAME_LENGTH) name: String?,
        @CookieValue(name = "name", required = false) rememberedName: String?
    ): String {
        // An absent `name` falls back to the cookie; an explicit empty `name`
        // (?name=) intentionally does not — it means "greet me generically
        // this time", without touching what's remembered.
        val effectiveName = name ?: rememberedName ?: ""

        if (!name.isNullOrBlank()) {
            response.addHeader(
                HttpHeaders.SET_COOKIE,
                // Cookie values cannot hold spaces or non-ASCII letters (e.g. "José María"),
                // so the name is URL-encoded here; @CookieValue decodes it on the way back.
                ResponseCookie.from("name", UriUtils.encode(name, StandardCharsets.UTF_8))
                    .maxAge(NAME_COOKIE_MAX_AGE).path("/").build().toString()
            )
            // Only a name sent in this request is recorded, not one from the cookie
            greetingHistory.record(name, LocaleContextHolder.getLocale())
        }

        model.addAttribute("message", greetingFor(effectiveName))
        model.addAttribute("name", effectiveName)

        // Read after record(), so a name sent now is already in the list.
        // A database error hides the list but never breaks the page.
        val history = try {
            greetingHistory.latest()
        } catch (e: DataAccessException) {
            log.warn("Could not read the greeting history", e)
            null
        }
        model.addAttribute("history", history ?: emptyList<GreetingView>())
        model.addAttribute("historyUnavailable", history == null)
        // Where the page's live stream starts: the newest id shown, 0 if none,
        // and no value at all if the history could not be read (no live updates then)
        model.addAttribute("lastGreetingId", history?.let { h -> h.maxOfOrNull { it.id } ?: 0L })
        return "welcome"
    }

    /** Resolves the greeting text for [name] in the current request's locale. */
    private fun greetingFor(name: String): String {
        val locale = LocaleContextHolder.getLocale()
        return if (name.isNotBlank()) {
            messageSource.getMessage("greeting.named", arrayOf(name), locale)
        } else {
            messageSource.getMessage("greeting.default", null, locale)
        }
    }
}

/** JSON counterpart of the welcome page, at `/api/hello`. */
@RestController
@Validated
class HelloApiController(
    private val messageSource: MessageSource,
    private val greetingHistory: GreetingHistory
) {

    /**
     * Returns a locale-aware JSON greeting.
     *
     * Unlike [HelloController.welcome], this endpoint does not remember the
     * name across requests — every call must be explicit.
     * A non-blank [name] is recorded in the greeting history.
     *
     * @param name name to greet, at most [HelloController.MAX_NAME_LENGTH] characters.
     *             If absent or empty, the localized `greeting.defaultName`
     *             ("World", "Mundo") is used instead.
     * @return a map with `message` and `timestamp`.
     */
    @GetMapping("/api/hello", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun helloApi(
        @RequestParam(required = false) @Size(max = HelloController.MAX_NAME_LENGTH) name: String?
    ): Map<String, String> {
        val locale = LocaleContextHolder.getLocale()
        if (!name.isNullOrBlank()) greetingHistory.record(name, locale)
        val effectiveName = name?.takeIf { it.isNotEmpty() }
            ?: messageSource.getMessage("greeting.defaultName", null, locale)
        return mapOf(
            "message" to messageSource.getMessage("greeting.named", arrayOf(effectiveName), locale),
            "timestamp" to Instant.now().toString()
        )
    }
}
