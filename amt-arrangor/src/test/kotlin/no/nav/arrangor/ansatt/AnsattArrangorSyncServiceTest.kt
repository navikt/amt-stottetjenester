package no.nav.arrangor.ansatt

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.domain.AnsattRolle
import org.junit.jupiter.api.Test

class AnsattArrangorSyncServiceTest(
    private val ansattArrangorSyncService: AnsattArrangorSyncService,
    private val ansattRepository: AnsattRepository,
    @MockkBean private val ansattArrangorRepository: AnsattArrangorRepository,
) : IntegrationTest() {
    @Test
    fun `normalisert lagringsfeil ruller tilbake opprettelse av ansatt`() {
        val arrangor = testDatabase.insertArrangor()
        val nyAnsatt = testDatabase.ansatt(
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorId = arrangor.id,
                    roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                    veileder = emptyList(),
                    koordinator = emptyList(),
                ),
            ),
        )
        every { ansattArrangorRepository.getArrangorerForAnsatt(any()) } returns emptyList()
        every {
            ansattArrangorRepository.replaceForAnsatt(nyAnsatt.id, nyAnsatt.arrangorer)
        } throws RuntimeException("Simulert feil i normalisert lagring")

        shouldThrow<RuntimeException> {
            ansattArrangorSyncService.opprettAnsatt(nyAnsatt)
        }.message shouldBe "Simulert feil i normalisert lagring"

        ansattRepository.get(nyAnsatt.id) shouldBe null
    }

    @Test
    fun `opprettelse sender bare ansattrelasjoner til normalisert repository`() {
        val arrangor = testDatabase.insertArrangor()
        val arrangorer = listOf(
            ArrangorDbo(
                arrangorId = arrangor.id,
                roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                veileder = emptyList(),
                koordinator = emptyList(),
            ),
        )
        val nyAnsatt = testDatabase.ansatt(arrangorer = arrangorer)
        every { ansattArrangorRepository.getArrangorerForAnsatt(nyAnsatt.id) } returns arrangorer
        every {
            ansattArrangorRepository.replaceForAnsatt(nyAnsatt.id, arrangorer)
        } returns Unit

        val lagret = ansattArrangorSyncService.opprettAnsatt(nyAnsatt)

        verify(exactly = 1) { ansattArrangorRepository.replaceForAnsatt(nyAnsatt.id, arrangorer) }
        lagret.arrangorer shouldBe arrangorer
    }
}
