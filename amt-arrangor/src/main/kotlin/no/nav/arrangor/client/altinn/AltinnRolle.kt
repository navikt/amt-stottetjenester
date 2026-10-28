package no.nav.arrangor.client.altinn

import no.nav.arrangor.domain.AnsattRolle

data class AltinnRolle(
    val organisasjonsnummer: String,
    val roller: List<AnsattRolle>,
)
