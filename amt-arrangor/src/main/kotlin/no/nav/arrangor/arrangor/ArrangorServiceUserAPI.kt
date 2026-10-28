package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.utils.Orgnummer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/service/arrangor")
class ArrangorServiceUserAPI(
    private val arrangorService: ArrangorService,
) {
    @GetMapping("/organisasjonsnummer/{orgnummer}")
    fun getArrangor(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangor {
        val gyldigOrgnummer = validerOrganisasjonsnummer(orgnummer)
        return arrangorService.getArrangorMedOverordnetArrangor(gyldigOrgnummer)
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ArrangorMedOverordnetArrangor = arrangorService.getArrangorMedOverordnetArrangor(id)
        ?: throw NoSuchElementException("Arrangør med id $id eksisterer ikke")

    private fun validerOrganisasjonsnummer(organisasjonsnummer: String): String {
        val trimmetOrganisasjonsnummer = organisasjonsnummer.trim()
        if (!Orgnummer.erGyldig(trimmetOrganisasjonsnummer)) {
            throw IllegalArgumentException("Ugyldig organisasjonsnummer $trimmetOrganisasjonsnummer")
        }
        return trimmetOrganisasjonsnummer
    }
}
