package es.unizar.webeng.hello.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

// One rule exists today (name length), so the handler doesn't need to inspect
// which constraint failed — it always reports the same, single error message.
@ControllerAdvice
class ValidationExceptionHandler(
    private val messageSource: MessageSource
) {

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleInvalidName(request: HttpServletRequest): Any {
        val locale = LocaleContextHolder.getLocale()
        val errorMessage = messageSource.getMessage(
            "name.tooLong", arrayOf(HelloController.MAX_NAME_LENGTH), locale
        )

        return if (request.requestURI.startsWith("/api/")) {
            ResponseEntity.badRequest().body(mapOf("error" to errorMessage))
        } else {
            ModelAndView("welcome", mapOf(
                "message" to messageSource.getMessage("greeting.default", null, locale),
                "name" to "",
                "error" to errorMessage
            )).apply { status = HttpStatus.BAD_REQUEST }
        }
    }
}
