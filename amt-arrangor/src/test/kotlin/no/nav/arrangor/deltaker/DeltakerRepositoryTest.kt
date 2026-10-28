package no.nav.arrangor.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.kafka.model.Deltaker
import no.nav.arrangor.kafka.model.DeltakerStatus
import no.nav.arrangor.kafka.model.DeltakerStatusType
import no.nav.arrangor.utils.shouldBeCloseTo
import no.nav.arrangor.utils.shouldBeCloseToNow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class DeltakerRepositoryTest(
    private val deltakerRepository: DeltakerRepository,
) : RepositoryTestBase() {
    @Test
    fun `insertOrUpdate skal lagre deltaker dersom den ikke finnes fra for`() {
        deltakerRepository.insertOrUpdate(deltakerInTest)

        val deltakerInDb = deltakerRepository.get(deltakerInTest.id)

        assertSoftly(deltakerInDb.shouldNotBeNull()) {
            id shouldBe deltakerInTest.id

            assertSoftly(deltakerInDb.status) {
                type shouldBe DeltakerStatusType.DELTAR
                gyldigFra shouldBeCloseTo now.plusDays(1)
                opprettetDato.shouldBeCloseToNow()
            }
        }
    }

    @Test
    fun `insertOrUpdate skal oppdatere eksisterende deltaker dersom den finnes fra for`() {
        deltakerRepository.insertOrUpdate(deltakerInTest)

        val updatedDeltaker = deltakerInTest.copy(
            status = DeltakerStatus(
                type = DeltakerStatusType.IKKE_AKTUELL,
                gyldigFra = now.plusDays(2),
                opprettetDato = now.plusHours(1),
            ),
        )

        deltakerRepository.insertOrUpdate(updatedDeltaker)

        val deltakerInDb = deltakerRepository.get(deltakerInTest.id)

        assertSoftly(deltakerInDb.shouldNotBeNull()) {
            id shouldBe deltakerInTest.id

            assertSoftly(deltakerInDb.status) {
                type shouldBe DeltakerStatusType.IKKE_AKTUELL
                gyldigFra shouldBeCloseTo now.plusDays(2)
                opprettetDato shouldBeCloseTo now.plusHours(1)
            }
        }
    }

    companion object {
        private val now = LocalDateTime.now()

        private val statusInTest = DeltakerStatus(
            type = DeltakerStatusType.DELTAR,
            gyldigFra = now.plusDays(1),
            opprettetDato = now,
        )

        private val deltakerInTest = Deltaker(
            id = UUID.randomUUID(),
            status = statusInTest,
        )
    }
}
