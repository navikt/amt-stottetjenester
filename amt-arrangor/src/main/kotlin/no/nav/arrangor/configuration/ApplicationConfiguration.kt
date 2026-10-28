package no.nav.arrangor.configuration

import no.nav.common.rest.filter.LogRequestFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {
    @Bean
    fun logFilterRegistrationBean(): FilterRegistrationBean<LogRequestFilter> {
        val registration = FilterRegistrationBean<LogRequestFilter>()
        registration.setFilter(LogRequestFilter("amt-arrangor", false))
        registration.order = 1
        registration.addUrlPatterns("/*")
        return registration
    }
}
