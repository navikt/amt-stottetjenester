package no.nav.enhetsregister.controller

import no.nav.enhetsregister.controller.dto.EnhetDto
import no.nav.enhetsregister.service.EnhetService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/enhet")
class EnhetController(
    private val enhetService: EnhetService,
) {
    @GetMapping("/{organisasjonsnummer}")
    fun hentEnhet(
        @PathVariable("organisasjonsnummer") organisasjonsnummer: String,
    ): EnhetDto {
        validerOrganisasjonsnummer(organisasjonsnummer)
        return enhetService
            .hentEnhet(organisasjonsnummer)
            ?.let(::EnhetDto)
            ?: throw NoSuchElementException("Fant ikke enhet med organisasjonsnummer: $organisasjonsnummer")
    }

    companion object {
        private fun validerOrganisasjonsnummer(organisasjonsnummer: String) {
            if (organisasjonsnummer.trim().length != 9 || !organisasjonsnummer.trim().matches("""\d{9}""".toRegex())) {
                throw IllegalArgumentException("Ugyldig organisasjonsnummer $organisasjonsnummer")
            }
        }
    }
}
