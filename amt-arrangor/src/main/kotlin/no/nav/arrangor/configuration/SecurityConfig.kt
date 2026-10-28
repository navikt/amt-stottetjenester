package no.nav.arrangor.configuration

import no.nav.amt.lib.spring.boot.security.InternalAuthorizationManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationManagerResolver
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtAudienceValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(InternalAuthorizationManager::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        internalAuthorizationManager: InternalAuthorizationManager,
        @Qualifier("azureAdAuthenticationManager") azureAdAuthenticationManager: AuthenticationManager,
        @Qualifier("tokenXAuthenticationManager") tokenXAuthenticationManager: AuthenticationManager,
        restAuthenticationEntryPoint: RestAuthenticationEntryPoint,
        restAccessDeniedHandler: RestAccessDeniedHandler,
    ): SecurityFilterChain {
        val ansattApi = PathPatternRequestMatcher.withDefaults().matcher("/api/ansatt/**")

        http {
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
            logout { disable() }
            oauth2ResourceServer {
                // Ansatt-API-et kalles av sluttbrukere (TokenX), alt annet av andre systemer (Azure AD).
                // Resolveren velger derfor riktig AuthenticationManager (og dermed riktig issuer/audience)
                // basert på hvilken sti requesten treffer.
                authenticationManagerResolver = AuthenticationManagerResolver { request ->
                    if (ansattApi.matches(request)) {
                        tokenXAuthenticationManager
                    } else {
                        azureAdAuthenticationManager
                    }
                }
                // BearerTokenAuthenticationFilter validerer selve JWT-en (signatur, issuer, audience, utløp)
                // og håndterer feil her selv, FØR ExceptionTranslationFilter/exceptionHandling nedenfor
                // rekker å se dem. Uten denne bruker resource server-modulen sin egen standard-entrypoint
                // (kun WWW-Authenticate-header, ikke vår JSON-kontrakt), så den må settes eksplisitt her også.
                authenticationEntryPoint = restAuthenticationEntryPoint
            }
            exceptionHandling {
                // Dekker alle andre autentiseringsfeil enn selve JWT-valideringen over,
                // f.eks. når det ikke finnes noen Authorization-header i det hele tatt.
                authenticationEntryPoint = restAuthenticationEntryPoint
                accessDeniedHandler = restAccessDeniedHandler
            }
            authorizeHttpRequests {
                // Reglene evalueres i rekkefølge, og første treff vinner - derfor må de mest spesifikke
                // stiene (health/prometheus, /internal, /api/ansatt) stå før catch-all-regelen til slutt.
                authorize(
                    EndpointRequest.to(
                        HealthEndpoint::class.java,
                        PrometheusScrapeEndpoint::class.java,
                    ),
                    permitAll,
                )

                // internalAuthorizationManager slipper kun gjennom kall fra loopback (127.0.0.1),
                // dvs. interne kall i samme pod - se no.nav.amt.lib.spring.boot.security.InternalAuthorizationManager.
                authorize("/internal/**", internalAuthorizationManager)

                // Ansatt-API-et krever kun gyldig token (fra tokenXAuthenticationManager over),
                // ikke rollen access_as_application som gjelder for system-til-system-kall nedenfor.
                authorize("/api/ansatt/**", authenticated)

                // Alt annet er system-til-system-kall og krever Azure AD-rollen access_as_application.
                authorize(anyRequest, hasRole("access_as_application"))
            }
        }

        return http.build()
    }

    @Bean
    @Primary // Skiller de to AuthenticationManager-beanene fra hverandre der de injiseres uten @Qualifier.
    fun azureAdAuthenticationManager(
        @Value($$"${AZURE_OPENID_CONFIG_ISSUER}") azureAdIssuer: String,
        @Value($$"${AZURE_APP_CLIENT_ID}") azureAdAudience: String,
    ): AuthenticationManager = authenticationManager(
        issuer = azureAdIssuer,
        audience = azureAdAudience,
        authenticationConverter = azureAdAuthenticationConverter(),
    )

    @Bean
    fun tokenXAuthenticationManager(
        @Value($$"${TOKEN_X_ISSUER}") tokenXIssuer: String,
        @Value($$"${TOKEN_X_CLIENT_ID}") tokenXAudience: String,
    ): AuthenticationManager = authenticationManager(
        issuer = tokenXIssuer,
        audience = tokenXAudience,
        authenticationConverter = JwtAuthenticationConverter(),
    )

    private fun authenticationManager(
        issuer: String,
        audience: String,
        authenticationConverter: JwtAuthenticationConverter,
    ): AuthenticationManager {
        // withIssuerLocation gjør samme OIDC-discovery som JwtDecoders.fromIssuerLocation, men
        // returnerer NimbusJwtDecoder direkte (ikke det generelle JwtDecoder-interfacet), så vi
        // slipper unchecked cast for å nå setJwtValidator - som trengs for å legge på
        // audience-sjekken i tillegg til standardvalideringen (issuer, utløp). Spring validerer
        // kun issuer og utløp som standard - audience må legges til eksplisitt for å hindre at et
        // token utstedt til en annen klient (men med samme issuer) godtas her.
        val decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                JwtAudienceValidator(audience),
            ),
        )

        return JwtAuthenticationProvider(decoder)
            .apply { setJwtAuthenticationConverter(authenticationConverter) }
            .let { ProviderManager(listOf(it)) }
    }

    private fun azureAdAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter(
            JwtGrantedAuthoritiesConverter().apply {
                // Azure AD-tokens har rollene i "roles"-claimet (App Roles), ikke standard "scope"/"scp"
                // som JwtGrantedAuthoritiesConverter bruker som default. ROLE_-prefikset gjør at
                // hasRole("access_as_application") over kan matche mot ROLE_access_as_application.
                setAuthoritiesClaimName("roles")
                setAuthorityPrefix("ROLE_")
            },
        )
    }
}
