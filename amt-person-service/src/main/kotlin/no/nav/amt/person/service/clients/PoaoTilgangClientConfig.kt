package no.nav.amt.person.service.clients

import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangClient
import no.nav.poao_tilgang.client.PoaoTilgangHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager

/**
 * Konfigurerer klienten som [no.nav.amt.person.service.navbruker.NavBrukerService] bruker for å
 * sjekke om en person er skjermet når en ny Nav-bruker opprettes.
 */
@Configuration(proxyBeanMethods = false)
class PoaoTilgangClientConfig {
    @Bean
    fun poaoTilgangClient(
        @Value($$"${POAO_TILGANG_URL}") poaoTilgangUrl: String,
        authorizedClientManager: OAuth2AuthorizedClientManager,
    ): PoaoTilgangClient = PoaoTilgangCachedClient(
        PoaoTilgangHttpClient(
            baseUrl = poaoTilgangUrl,
            tokenProvider = {
                val request = OAuth2AuthorizeRequest
                    .withClientRegistrationId("poao-tilgang")
                    .principal("amt-person-service")
                    .build()
                authorizedClientManager.authorize(request)?.accessToken?.tokenValue
                    ?: throw IllegalStateException("Fikk null access_token fra OAuth2 for poao-tilgang")
            },
        ),
    )
}
