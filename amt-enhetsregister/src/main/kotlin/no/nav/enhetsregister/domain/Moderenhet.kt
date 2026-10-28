package no.nav.enhetsregister.domain

data class Moderenhet(
    val organisasjonsnummer: String,
    val navn: String,
    val slettedato: String?,
)
