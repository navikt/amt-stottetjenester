package no.nav.amt.person.service.clients.kodeverk

import no.nav.amt.person.service.clients.KODEVERK_API_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange

@ClientRegistrationId(KODEVERK_API_CLIENT_ID)
interface KodeverkApi {
    @GetExchange("/api/v1/kodeverk/Postnummer/koder/betydninger")
    fun hentPostnummerBetydninger(
        @RequestParam ekskluderUgyldige: Boolean,
        @RequestParam oppslagsdato: String,
        @RequestParam spraak: String,
    ): ResponseEntity<GetKodeverkKoderBetydningerResponse>
}
