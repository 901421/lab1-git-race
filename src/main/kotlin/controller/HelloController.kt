package es.unizar.webeng.hello.controller

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Controller
class HelloController(
    private val messageSource: MessageSource
) {

    @GetMapping("/")
    fun welcome(
        model: Model,
        @RequestParam(defaultValue = "") name: String
    ): String {
        model.addAttribute("message", greetingFor(name))
        model.addAttribute("name", name)
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
class HelloApiController(
    private val messageSource: MessageSource
) {

    @GetMapping("/api/hello", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun helloApi(@RequestParam(defaultValue = "World") name: String): Map<String, String> {
        val locale = LocaleContextHolder.getLocale()
        return mapOf(
            "message" to messageSource.getMessage("greeting.named", arrayOf(name), locale),
            "timestamp" to Instant.now().toString()
        )
    }
}
