package no.nav.amt.altinn.acl.client.altinn

import no.nav.amt.altinn.acl.domain.RolleType

data class AuthorizedParty(
    val organizationNumber: String?,
    val authorizedResources: Set<String>,
    val subunits: List<AuthorizedParty>,
) {
    /**
     * Finner tilganger for etterspurte ressurser i denne organisasjonen og alle underenhetene.
     * Organisasjoner uten organisasjonsnummer gir ingen egne tilganger, men underenhetene behandles fortsatt.
     */
    fun finnTilganger(resourceIds: Set<String>): List<Tilgang> {
        val tilganger = organizationNumber
            ?.let {
                authorizedResources
                    .intersect(resourceIds)
                    .map { Tilgang(RolleType.fromResourceId(it), organizationNumber) }
            }
            ?: emptyList()

        val underenhetTilganger = subunits.flatMap { it.finnTilganger(resourceIds) }

        return tilganger + underenhetTilganger
    }
}
