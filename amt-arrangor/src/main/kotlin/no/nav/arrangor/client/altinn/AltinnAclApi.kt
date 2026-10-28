package no.nav.arrangor.client.altinn

import no.nav.arrangor.client.AMT_ALTINN_CLIENT_ID
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

@ClientRegistrationId(AMT_ALTINN_CLIENT_ID)
interface AltinnAclApi {
    @PostExchange("/api/v1/rolle/tiltaksarrangor")
    fun hentRoller(
        @RequestBody request: HentRollerRequest,
    ): ResponseWrapper

    data class HentRollerRequest(
        val personident: String,
    )

    data class ResponseWrapper(
        val roller: List<ResponseEntry>,
    )

    data class ResponseEntry(
        val organisasjonsnummer: String,
        val roller: List<String>,
    )
}
