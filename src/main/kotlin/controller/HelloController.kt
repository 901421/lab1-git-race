package es.unizar.webeng.hello.controller

import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Size
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
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
import java.time.Duration
import java.time.Instant

@Controller
@Validated
class HelloController(
    private val messageSource: MessageSource
) {

    companion object {
        const val MAX_NAME_LENGTH = 50
        private val NAME_COOKIE_MAX_AGE: Duration = Duration.ofDays(30)
    }

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
                ResponseCookie.from("name", name).maxAge(NAME_COOKIE_MAX_AGE).path("/").build().toString()
            )
        }

        model.addAttribute("message", greetingFor(effectiveName))
        model.addAttribute("name", effectiveName)
        return "welcome"
    }

    private fun greetingFor(name: String): String {
        val locale = LocaleContextHolder.getLocale()
        return if (name.isNotBlank()) {
            messageSource.getMessage("greeting.named", arrayOf(name), locale)
        } else {
            messageSource.getMessage("greeting.default", null, locale)
        }
    }
}

@RestController
@Validated
class HelloApiController(
    private val messageSource: MessageSource
) {

    @GetMapping("/api/hello", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun helloApi(
        @RequestParam(defaultValue = "World") @Size(max = HelloController.MAX_NAME_LENGTH) name: String
    ): Map<String, String> {
        val locale = LocaleContextHolder.getLocale()
        return mapOf(
            "message" to messageSource.getMessage("greeting.named", arrayOf(name), locale),
            "timestamp" to Instant.now().toString()
        )
    }
}
