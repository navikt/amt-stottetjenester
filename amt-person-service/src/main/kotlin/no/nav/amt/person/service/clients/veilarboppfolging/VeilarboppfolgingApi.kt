package no.nav.amt.person.service.clients.veilarboppfolging

import no.nav.amt.person.service.clients.VEILARBOPPFOLGING_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import java.time.ZonedDateTime
import java.util.UUID

@ClientRegistrationId(VEILARBOPPFOLGING_CLIENT_ID)
interface VeilarboppfolgingApi {
    @PostExchange("/api/v3/hent-veileder")
    fun hentVeileder(
        @RequestBody request: PersonRequest,
    ): ResponseEntity<HentBrukersVeilederResponse>

    @PostExchange("/api/v3/oppfolging/hent-perioder")
    fun hentOppfolgingsperioder(
        @RequestBody request: PersonRequest,
    ): ResponseEntity<List<OppfolgingPeriodeResponse>>

    data class PersonRequest(
        val fnr: String,
    )

    data class HentBrukersVeilederResponse(
        val veilederIdent: String,
    )

    data class OppfolgingPeriodeResponse(
        val uuid: UUID,
        val startDato: ZonedDateTime,
        val sluttDato: ZonedDateTime?,
    )
}
