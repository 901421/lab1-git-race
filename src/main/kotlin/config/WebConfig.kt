package es.unizar.webeng.hello.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.CookieLocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import java.time.Duration

/**
 * Resolves the visitor's locale from the `Accept-Language` header, lets it be
 * overridden with a `?lang=` query parameter, and remembers the chosen locale
 * in a cookie so it survives across visits.
 *
 * No fixed default locale is set on purpose: without a cookie or `?lang=`,
 * resolution falls through to the request's `Accept-Language` header. Hardcoding
 * a default here would silently ignore that header on every first visit.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    @Bean
    fun localeResolver(): LocaleResolver =
        CookieLocaleResolver("lang").apply {
            setCookieMaxAge(Duration.ofDays(30))
        }

    @Bean
    fun localeChangeInterceptor(): LocaleChangeInterceptor =
        LocaleChangeInterceptor().apply { paramName = "lang" }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(localeChangeInterceptor())
    }
}
