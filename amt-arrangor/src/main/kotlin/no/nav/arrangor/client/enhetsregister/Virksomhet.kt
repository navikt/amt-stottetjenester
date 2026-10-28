package no.nav.arrangor.client.enhetsregister

data class Virksomhet(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhetOrganisasjonsnummer: String?,
    val overordnetEnhetNavn: String?,
)
