package no.nav.enhetsregister.client

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange(accept = ["application/json"])
interface BronnoysundApi {
    @GetExchange("/enhetsregisteret/api/oppdateringer/enheter")
    fun hentModerenhetOppdateringer(
        @RequestParam("oppdateringsid") fraOppdateringId: Int,
        @RequestParam("size") size: Int,
    ): ResponseEntity<HentModerenhetOppdateringerResponse>

    @GetExchange("/enhetsregisteret/api/oppdateringer/underenheter")
    fun hentUnderenhetOppdateringer(
        @RequestParam("oppdateringsid") fraOppdateringId: Int,
        @RequestParam("size") size: Int,
    ): ResponseEntity<HentUnderenhetOppdateringerResponse>

    @GetExchange("/enhetsregisteret/api/enheter/{organisasjonsnummer}")
    fun hentModerenhet(
        @PathVariable("organisasjonsnummer") organisasjonsnummer: String,
    ): ResponseEntity<ModerenhetDto>

    @GetExchange("/enhetsregisteret/api/underenheter/{organisasjonsnummer}")
    fun hentUnderenhet(
        @PathVariable("organisasjonsnummer") organisasjonsnummer: String,
    ): ResponseEntity<UnderenhetDto>
}
