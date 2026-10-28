package no.nav.amt.altinn.acl.client.altinn

import no.nav.amt.altinn.acl.domain.RolleType

data class Tilgang(
    val rolle: RolleType,
    val organisasjonsnummer: String,
)
