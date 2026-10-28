package no.nav.enhetsregister.domain

data class Underenhet(
    val organisasjonsnummer: String,
    val navn: String,
    val slettedato: String?,
    val overordnetEnhet: String?,
)
