package no.nav.amt.altinn.acl.client.altinn

import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.domain.RolleType
import org.junit.jupiter.api.Test

class AuthorizedPartyTest {
    @Test
    fun `finnTilganger - returnerer bare etterspurte ressurser for organisasjonen`() {
        val party = AuthorizedParty(
            organizationNumber = "123456789",
            authorizedResources = setOf(
                RolleType.KOORDINATOR.resourceId,
                RolleType.VEILEDER.resourceId,
            ),
            subunits = emptyList(),
        )

        party.finnTilganger(
            resourceIds = setOf(RolleType.VEILEDER.resourceId),
        ) shouldBe listOf(
            Tilgang(
                rolle = RolleType.VEILEDER,
                organisasjonsnummer = "123456789",
            ),
        )
    }

    @Test
    fun `finnTilganger - inkluderer tilganger fra underenheter pa alle nivaer`() {
        val party = AuthorizedParty(
            organizationNumber = "111111111",
            authorizedResources = setOf(RolleType.KOORDINATOR.resourceId),
            subunits = listOf(
                AuthorizedParty(
                    organizationNumber = "222222222",
                    authorizedResources = setOf(RolleType.VEILEDER.resourceId),
                    subunits = listOf(
                        AuthorizedParty(
                            organizationNumber = "333333333",
                            authorizedResources = setOf(RolleType.KOORDINATOR.resourceId),
                            subunits = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        party.finnTilganger(
            resourceIds = RolleType.entries.map { it.resourceId }.toSet(),
        ) shouldBe listOf(
            Tilgang(
                rolle = RolleType.KOORDINATOR,
                organisasjonsnummer = "111111111",
            ),
            Tilgang(
                rolle = RolleType.VEILEDER,
                organisasjonsnummer = "222222222",
            ),
            Tilgang(
                rolle = RolleType.KOORDINATOR,
                organisasjonsnummer = "333333333",
            ),
        )
    }

    @Test
    fun `finnTilganger - hopper over organisasjon uten organisasjonsnummer men behandler underenheter`() {
        val party = AuthorizedParty(
            organizationNumber = null,
            authorizedResources = setOf(RolleType.KOORDINATOR.resourceId),
            subunits = listOf(
                AuthorizedParty(
                    organizationNumber = "222222222",
                    authorizedResources = setOf(RolleType.VEILEDER.resourceId),
                    subunits = emptyList(),
                ),
            ),
        )

        party.finnTilganger(
            resourceIds = RolleType.entries.map { it.resourceId }.toSet(),
        ) shouldBe listOf(
            Tilgang(
                rolle = RolleType.VEILEDER,
                organisasjonsnummer = "222222222",
            ),
        )
    }

    @Test
    fun `finnTilganger - returnerer tom liste nar ingen ressurser samsvarer`() {
        val party = AuthorizedParty(
            organizationNumber = "123456789",
            authorizedResources = setOf(RolleType.KOORDINATOR.resourceId),
            subunits = emptyList(),
        )

        party.finnTilganger(
            resourceIds = setOf(RolleType.VEILEDER.resourceId),
        ) shouldBe emptyList()
    }
}
