package no.nav.amt.person.service.clients.norg

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.util.UriBuilderFactory

interface NorgApi {
    @GetExchange("/norg2/api/v1/enhet/{enhetId}")
    fun hentNavEnhet(
        @PathVariable("enhetId") enhetId: String,
    ): ResponseEntity<NorgNavEnhetDto>

    @GetExchange("/norg2/api/v1/enhet")
    fun hentNavEnheter(
        @RequestParam("enhetsnummerListe") enhetsnummerListe: String,
        uriBuilderFactory: UriBuilderFactory,
    ): ResponseEntity<List<NorgNavEnhetDto>>
}
