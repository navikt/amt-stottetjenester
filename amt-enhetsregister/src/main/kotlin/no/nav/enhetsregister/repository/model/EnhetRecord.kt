package no.nav.enhetsregister.repository.model

data class EnhetRecord(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhet: String?,
)
