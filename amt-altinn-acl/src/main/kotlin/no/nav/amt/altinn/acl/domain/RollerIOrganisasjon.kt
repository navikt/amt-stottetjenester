package no.nav.amt.altinn.acl.domain

data class RollerIOrganisasjon(
    val organisasjonsnummer: String,
    val roller: List<Rolle>,
)
