package no.nav.amt.altinn.acl.config

import jakarta.servlet.DispatcherType
import no.nav.amt.lib.spring.boot.security.InternalAuthorizationManager
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher

@Configuration(proxyBeanMethods = false)
@Import(InternalAuthorizationManager::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        internalAuthorizationManager: InternalAuthorizationManager,
    ): SecurityFilterChain {
        http {
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
            logout { disable() }
            oauth2ResourceServer { jwt { } }
            authorizeHttpRequests {
                // Bevarer opprinnelig feilrespons når et internt kall uten bearer-token redispatches til /error.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(
                    EndpointRequest.to(
                        HealthEndpoint::class.java,
                        PrometheusScrapeEndpoint::class.java,
                    ),
                    permitAll,
                )
                authorize("/internal/**", internalAuthorizationManager)
                authorize(anyRequest, hasRole(ACCESS_AS_APPLICATION_ROLE))
            }
        }

        return http.build()
    }

    companion object {
        const val ACCESS_AS_APPLICATION_ROLE = "access_as_application"
    }
}
