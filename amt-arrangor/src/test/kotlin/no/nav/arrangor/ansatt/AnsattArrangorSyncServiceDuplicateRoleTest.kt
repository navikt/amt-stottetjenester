package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.domain.AnsattRolle
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class AnsattArrangorSyncServiceDuplicateRoleTest(
    private val ansattArrangorSyncService: AnsattArrangorSyncService,
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
) : IntegrationTest() {
    @Test
    fun `dedupliserer kolliderende rolleperioder i normaliserte tabeller`() {
        // Arrange
        val arrangor = testDatabase.insertArrangor()
        val veilederGyldigFra = ZonedDateTime.parse("2025-01-01T00:00:00Z")
        val annenVeilederGyldigFra = ZonedDateTime.parse("2025-02-01T00:00:00Z")
        val koordinatorGyldigFra = ZonedDateTime.parse("2025-03-01T00:00:00Z")
        val subMicrosecondVeilederGyldigFra1 = ZonedDateTime.parse("2025-04-01T00:00:00.000000100Z")
        val subMicrosecondVeilederGyldigFra2 = ZonedDateTime.parse("2025-04-01T00:00:00.000000400Z")
        val tidligGyldigTil = ZonedDateTime.parse("2025-04-01T00:00:00Z")
        val senGyldigTil = ZonedDateTime.parse("2025-04-02T00:00:00Z")
        val arrangorDbo = ArrangorDbo(
            arrangorId = arrangor.id,
            roller = listOf(
                RolleDbo(AnsattRolle.VEILEDER, veilederGyldigFra, tidligGyldigTil),
                RolleDbo(AnsattRolle.VEILEDER, veilederGyldigFra, senGyldigTil),
                RolleDbo(
                    AnsattRolle.VEILEDER,
                    annenVeilederGyldigFra,
                    ZonedDateTime.parse("2025-05-01T00:00:00Z"),
                ),
                RolleDbo(AnsattRolle.KOORDINATOR, koordinatorGyldigFra, senGyldigTil),
                RolleDbo(AnsattRolle.KOORDINATOR, koordinatorGyldigFra, null),
                RolleDbo(AnsattRolle.VEILEDER, subMicrosecondVeilederGyldigFra1, tidligGyldigTil),
                RolleDbo(AnsattRolle.VEILEDER, subMicrosecondVeilederGyldigFra2, senGyldigTil),
            ),
            veileder = emptyList(),
            koordinator = emptyList(),
        )
        val ansatt = testDatabase.ansatt(arrangorer = listOf(arrangorDbo))

        // Act
        val lagretAnsatt = ansattArrangorSyncService.opprettAnsatt(ansatt)

        // Assert
        val ansattFraDatabase = ansattRepository.get(ansatt.id).shouldNotBeNull()
        val normaliserteArrangorer = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)
        lagretAnsatt.arrangorer shouldBe normaliserteArrangorer
        ansattFraDatabase.arrangorer shouldBe normaliserteArrangorer
        normaliserteArrangorer.single().roller shouldHaveSize 4
    }
}
