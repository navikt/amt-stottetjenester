package no.nav.amt.person.service.config

import no.nav.common.rest.filter.LogRequestFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.resilience.annotation.EnableResilientMethods

@EnableResilientMethods
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun logFilterRegistrationBean(): FilterRegistrationBean<LogRequestFilter> = FilterRegistrationBean<LogRequestFilter>().apply {
        setFilter(LogRequestFilter("amt-person-service", false))
        order = 1
        addUrlPatterns("/*")
    }
}
