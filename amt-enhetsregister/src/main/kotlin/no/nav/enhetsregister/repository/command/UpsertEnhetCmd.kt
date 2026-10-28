package no.nav.enhetsregister.repository.command

data class UpsertEnhetCmd(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhet: String? = null,
)
