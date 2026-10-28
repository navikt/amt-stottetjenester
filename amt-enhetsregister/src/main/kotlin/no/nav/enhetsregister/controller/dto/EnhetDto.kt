package no.nav.enhetsregister.controller.dto

import no.nav.enhetsregister.service.EnhetService

data class EnhetDto(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhetOrganisasjonsnummer: String?,
    val overordnetEnhetNavn: String?,
) {
    constructor(enhet: EnhetService.EnhetMedOverordnetEnhet) : this(
        organisasjonsnummer = enhet.organisasjonsnummer,
        navn = enhet.navn,
        overordnetEnhetOrganisasjonsnummer = enhet.overordnetEnhetOrganisasjonsnummer,
        overordnetEnhetNavn = enhet.overordnetEnhetNavn,
    )
}
