package no.nav.arrangor.kafka.model

data class VirksomhetDto(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhetOrganisasjonsnummer: String?,
)
