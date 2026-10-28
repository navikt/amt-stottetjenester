package no.nav.amt.altinn.acl.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldNotBeAfter
import io.kotest.matchers.date.shouldNotBeBefore
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.testutil.RepositoryTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(classes = [PersonRepository::class])
class PersonRepositoryTest(
    private val personRepository: PersonRepository,
) : RepositoryTestBase() {
    @Nested
    inner class OpprettMedSynkronisering {
        @Test
        fun `oppretter person med angitt synkroniseringstidspunkt`() {
            // Arrange
            val norskIdent = newNorskIdent()
            val lastSynchronized = OffsetDateTime.parse("2026-09-28T12:30:00Z").toZonedDateTime()

            // Act
            val person = personRepository.createAndSetSynchronized(norskIdent, lastSynchronized)

            // Assert
            person.norskIdent shouldBe norskIdent
            person.lastSynchronized.toInstant() shouldBe lastSynchronized.toInstant()
        }

        @Test
        fun `oppretter person med gjeldende synkroniseringstidspunkt som standard`() {
            // Arrange
            val norskIdent = newNorskIdent()
            val before = Instant.now()

            // Act
            val person = personRepository.createAndSetSynchronized(norskIdent)
            val after = Instant.now()

            // Assert
            assertSoftly(person) {
                this.norskIdent shouldBe norskIdent
                lastSynchronized.toInstant() shouldNotBeBefore before
                lastSynchronized.toInstant() shouldNotBeAfter after
            }
        }
    }

    @Nested
    inner class SettSynkronisert {
        @Test
        fun `oppdaterer tidspunkt for siste synkronisering`() {
            // Arrange
            val norskIdent = newNorskIdent()
            personRepository.createAndSetSynchronized(norskIdent, Instant.EPOCH.atZone(ZoneOffset.UTC))
            val lastSynchronized = OffsetDateTime.parse("2026-09-28T12:30:00Z").toZonedDateTime()

            // Act
            personRepository.setSynchronized(norskIdent, lastSynchronized)
            val updatedPerson = personRepository.get(norskIdent)

            // Assert
            updatedPerson.shouldNotBeNull()
            updatedPerson.lastSynchronized.toInstant() shouldBe lastSynchronized.toInstant()
        }

        @Test
        fun `bruker gjeldende tidspunkt som standard`() {
            // Arrange
            val norskIdent = newNorskIdent()
            personRepository.createAndSetSynchronized(norskIdent, Instant.EPOCH.atZone(ZoneOffset.UTC))
            val before = Instant.now()

            // Act
            personRepository.setSynchronized(norskIdent)
            val after = Instant.now()
            val updatedPerson = personRepository.get(norskIdent)

            // Assert
            assertSoftly {
                val synchronizedAt = updatedPerson.shouldNotBeNull().lastSynchronized.toInstant()
                synchronizedAt shouldNotBeBefore before
                synchronizedAt shouldNotBeAfter after
            }
        }
    }

    @Nested
    inner class HentUsynkronisertePersoner {
        @Test
        fun `returnerer personer før grenseverdien sortert etter synkroniseringstidspunkt`() {
            // Arrange
            val oldestIdent = newNorskIdent()
            val newerIdent = newNorskIdent()
            val cutoffIdent = newNorskIdent()
            val newerThanCutoffIdent = newNorskIdent()
            personRepository.createAndSetSynchronized(
                oldestIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").minusDays(2).toZonedDateTime(),
            )
            personRepository.createAndSetSynchronized(
                newerIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").minusDays(1).toZonedDateTime(),
            )
            personRepository.createAndSetSynchronized(
                cutoffIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").toZonedDateTime(),
            )
            personRepository.createAndSetSynchronized(
                newerThanCutoffIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").plusDays(1).toZonedDateTime(),
            )

            // Act
            val persons = personRepository.getUnsynchronizedPersons(
                maxSize = 10,
                synchronizedBefore = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            )

            // Assert
            persons.map { it.norskIdent } shouldBe listOf(oldestIdent, newerIdent)
        }

        @Test
        fun `begrenser antall returnerte personer`() {
            // Arrange
            val oldestIdent = newNorskIdent()
            val middleIdent = newNorskIdent()
            personRepository.createAndSetSynchronized(
                oldestIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").minusDays(3).toZonedDateTime(),
            )
            personRepository.createAndSetSynchronized(
                middleIdent,
                OffsetDateTime.parse("2026-01-01T00:00:00Z").minusDays(2).toZonedDateTime(),
            )
            personRepository.createAndSetSynchronized(
                newNorskIdent(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z").minusDays(1).toZonedDateTime(),
            )

            // Act
            val persons = personRepository.getUnsynchronizedPersons(
                maxSize = 2,
                synchronizedBefore = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            )

            // Assert
            persons.map { it.norskIdent } shouldBe listOf(oldestIdent, middleIdent)
        }
    }

    @Nested
    inner class Hent {
        @Test
        fun `returnerer personen når den finnes`() {
            // Arrange
            val norskIdent = newNorskIdent()
            val createdPerson = personRepository.createAndSetSynchronized(norskIdent)

            // Act
            val person = personRepository.get(norskIdent)

            // Assert
            person shouldBe createdPerson
        }

        @Test
        fun `returnerer null når personen ikke finnes`() {
            // Arrange
            val norskIdent = newNorskIdent()

            // Act
            val person = personRepository.get(norskIdent)

            // Assert
            person shouldBe null
        }
    }

    private fun newNorskIdent(): String = "ident-${UUID.randomUUID()}"
}
