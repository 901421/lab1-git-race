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

/**
 * Turns a failed `name` constraint into a controlled response instead of a
 * raw stack trace: a friendly inline message on the page, or a clean JSON
 * 400 on the API.
 *
 * One rule exists today (name length), so the handler doesn't need to
 * inspect which constraint failed — it always reports the same message.
 */
@ControllerAdvice
class ValidationExceptionHandler(
    private val messageSource: MessageSource
) {

    /**
     * Handles an invalid `name` for both [HelloController] and [HelloApiController].
     *
     * @param request used only to tell an API request (`/api/...`) from a
     *                page request, so the response shape matches the caller.
     * @return a [ResponseEntity] (API) or a [ModelAndView] (page), both with HTTP 400.
     */
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
