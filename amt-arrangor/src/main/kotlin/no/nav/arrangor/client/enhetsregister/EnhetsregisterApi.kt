package no.nav.arrangor.client.enhetsregister

import no.nav.arrangor.client.AMT_ENHETSREGISTER_CLIENT_ID
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange

@ClientRegistrationId(AMT_ENHETSREGISTER_CLIENT_ID)
interface EnhetsregisterApi {
    @GetExchange("/api/enhet/{orgNr}")
    fun hentVirksomhet(
        @PathVariable orgNr: String,
    ): Virksomhet
}
