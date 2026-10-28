package no.nav.amt.person.service.clients.krr

import no.nav.amt.person.service.clients.DIGDIR_KRR_PROXY_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

@ClientRegistrationId(DIGDIR_KRR_PROXY_CLIENT_ID)
interface KrrProxyApi {
    @PostExchange("/rest/v1/personer?inkluderSikkerDigitalPost=false")
    fun hentPersoner(
        @RequestBody request: PostPersonerRequest,
    ): ResponseEntity<PostPersonerResponse>

    data class PostPersonerRequest(
        val personidenter: Set<String>,
    )

    data class PostPersonerResponse(
        val personer: Map<String, KontaktinformasjonDto>,
        val feil: Map<String, String>,
    ) {
        data class KontaktinformasjonDto(
            val personident: String,
            val epostadresse: String?,
            val mobiltelefonnummer: String?,
        )
    }
}
