package no.nav.arrangor.ansatt.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.shouldBeCloseToNow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AnsattRepositoryTest(
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
) : RepositoryTestBase() {
    @Test
    fun `insertOrUpdate lagrer personalia uten relasjoner`() {
        val ansatt = testDatabase.ansatt(
            arrangorer = listOf(testDatabase.ansattArrangorDbo(roller = listOf(RolleDbo(AnsattRolle.VEILEDER)))),
        )

        val lagret = ansattRepository.insertOrUpdate(ansatt)

        assertSoftly(lagret) {
            id shouldBe ansatt.id
            personident shouldBe ansatt.personident
            fornavn shouldBe ansatt.fornavn
            mellomnavn shouldBe ansatt.mellomnavn
            etternavn shouldBe ansatt.etternavn
            arrangorer.shouldBeEmpty()
            modifiedAt.shouldBeCloseToNow()
            lastSynchronized.shouldBeCloseToNow()
        }
        ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id).shouldBeEmpty()
    }

    @Test
    fun `insertOrUpdate oppdaterer personalia uten a skrive arrangorer`() {
        val arrangor = testDatabase.insertArrangor()
        val original = testDatabase.insertAnsatt(
            arrangorer = listOf(testDatabase.ansattArrangorDbo(arrangorId = arrangor.id)),
        )
        val relasjonerFor = ansattArrangorRepository.getArrangorerForAnsatt(original.id)
        val oppdatert = ansattRepository.insertOrUpdate(
            original.copy(
                fornavn = "Oppdatert",
                mellomnavn = null,
                arrangorer = emptyList(),
            ),
        )

        oppdatert.fornavn shouldBe "Oppdatert"
        oppdatert.mellomnavn shouldBe null
        oppdatert.arrangorer shouldBe relasjonerFor
        ansattArrangorRepository.getArrangorerForAnsatt(original.id) shouldBe relasjonerFor
    }

    @Test
    fun `updatePersonalia endrer bare personalia`() {
        val arrangor = testDatabase.insertArrangor()
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(testDatabase.ansattArrangorDbo(arrangorId = arrangor.id)),
        )
        val relasjonerFor = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)

        ansattRepository.updatePersonalia(
            ansattId = ansatt.id,
            personident = "oppdatert-ident",
            fornavn = "Oppdatert",
            mellomnavn = null,
            etternavn = "Etternavn",
        ) shouldBe true

        val oppdatert = ansattRepository.get(ansatt.id)
        oppdatert?.personident shouldBe "oppdatert-ident"
        oppdatert?.fornavn shouldBe "Oppdatert"
        oppdatert?.mellomnavn shouldBe null
        oppdatert?.arrangorer shouldBe relasjonerFor
        ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id) shouldBe relasjonerFor
    }

    @Test
    fun `lesemetoder returnerer arrangorrelasjoner fra normaliserte tabeller`() {
        val arrangor = testDatabase.insertArrangor()
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(
                testDatabase.ansattArrangorDbo(
                    arrangorId = arrangor.id,
                    roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                ),
            ),
            lastSynchronized = LocalDateTime.now().minusDays(8),
        )
        val annen = testDatabase.insertAnsatt(arrangorer = emptyList())
        val forventet = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)
        forventet.single().arrangorId shouldBe arrangor.id

        ansattRepository.get(ansatt.id)?.arrangorer shouldBe forventet
        ansattRepository.get(ansatt.personident)?.arrangorer shouldBe forventet
        ansattRepository.getByPersonId(ansatt.personId)?.arrangorer shouldBe forventet
        ansattRepository.getAnsatte(listOf(ansatt.id, annen.id)).associate { it.id to it.arrangorer } shouldBe
            mapOf(ansatt.id to forventet, annen.id to emptyList())
        ansattRepository.getAll(0, 100).single { it.id == ansatt.id }.arrangorer shouldBe forventet
        ansattRepository.getToSynchronize(5, LocalDateTime.now().minusDays(7)).single().arrangorer shouldBe forventet
        ansattRepository.getAnsatteHosArrangor(arrangor.id).map { it.id } shouldBe listOf(ansatt.id)
    }

    @Test
    fun `updatePersonalia returnerer false nar ansatt ikke finnes`() {
        ansattRepository.updatePersonalia(
            ansattId = UUID.randomUUID(),
            personident = "ident",
            fornavn = "Fornavn",
            mellomnavn = null,
            etternavn = "Etternavn",
        ) shouldBe false
    }

    @Test
    fun `getAll returnerer tom liste nar tabellen er tom`() {
        ansattRepository.getAll(offset = 0, limit = 100).shouldBeEmpty()
    }

    @Test
    fun `getAll sorterer etter modified_at`() {
        val yesterday = LocalDateTime.now().minusDays(1)
        val now = LocalDateTime.now()
        val tomorrow = now.plusDays(1)
        ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()).copy(modifiedAt = now))
        ansattRepository.insertOrUpdate(
            testDatabase.ansatt(arrangorer = emptyList()).copy(modifiedAt = tomorrow),
        )
        val oldest = ansattRepository.insertOrUpdate(
            testDatabase.ansatt(arrangorer = emptyList()).copy(modifiedAt = yesterday),
        )

        val allAnsatte = ansattRepository.getAll(offset = 0, limit = 100)

        allAnsatte.size shouldBe 3
        allAnsatte.first().id shouldBe oldest.id
    }

    @Test
    fun `get returnerer ansatt pa id og personident`() {
        val ansatt = ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()))

        ansattRepository.get(ansatt.id) shouldBe ansatt
        ansattRepository.get(ansatt.personident) shouldBe ansatt
        ansattRepository.get(UUID.randomUUID()) shouldBe null
        ansattRepository.get(UUID.randomUUID().toString()) shouldBe null
    }

    @Test
    fun `getByPersonId returnerer ansatt`() {
        val ansatt = ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()))

        ansattRepository.getByPersonId(ansatt.personId) shouldBe ansatt
        ansattRepository.getByPersonId(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `getIdForPersonident returnerer id`() {
        val ansatt = ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()))

        ansattRepository.getIdForPersonident(ansatt.personident) shouldBe ansatt.id
        ansattRepository.getIdForPersonident(UUID.randomUUID().toString()) shouldBe null
    }

    @Test
    fun `getToSynchronize returnerer eldre synkroniserte rader`() {
        ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()))
        val older = ansattRepository.insertOrUpdate(
            testDatabase.ansatt(arrangorer = emptyList(), lastSynchronized = LocalDateTime.now().minusDays(8)),
        )

        ansattRepository.getToSynchronize(5, LocalDateTime.now().minusDays(7)) shouldBe listOf(older)
    }

    @Test
    fun `getToSynchronize begrenser antall og sorterer etter eldste`() {
        ansattRepository.insertOrUpdate(testDatabase.ansatt(arrangorer = emptyList()))
        val two = ansattRepository.insertOrUpdate(
            testDatabase.ansatt(arrangorer = emptyList(), lastSynchronized = LocalDateTime.now().minusDays(2)),
        )
        val three = ansattRepository.insertOrUpdate(
            testDatabase.ansatt(arrangorer = emptyList(), lastSynchronized = LocalDateTime.now().minusDays(1)),
        )

        ansattRepository.getToSynchronize(2, LocalDateTime.now().plusMinutes(7)) shouldContainInOrder listOf(two, three)
    }
}
