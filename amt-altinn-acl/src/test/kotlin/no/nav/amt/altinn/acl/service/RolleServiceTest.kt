package no.nav.amt.altinn.acl.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.verify
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.domain.RolleType.KOORDINATOR
import no.nav.amt.altinn.acl.domain.RolleType.VEILEDER
import no.nav.amt.altinn.acl.domain.RollerIOrganisasjon
import no.nav.amt.altinn.acl.repository.PersonRepository
import no.nav.amt.altinn.acl.repository.RolleRepository
import no.nav.amt.altinn.acl.testutil.IntegrationTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RolleServiceTest(
    private val rolleService: RolleService,
    private val personRepository: PersonRepository,
    private val rolleRepository: RolleRepository,
    private val jdbcTemplate: JdbcTemplate,
) : IntegrationTest() {
    @Nested
    inner class HentRollerForPerson {
        @Test
        fun `oppretter person og henter roller fra Altinn når personen ikke finnes`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            mockAltinnRoller(norskIdent, listOf(KOORDINATOR, VEILEDER), listOf(organisasjonsnummer))

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)

            // Assert
            verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
            roller.size shouldBe 1
            hasRolle(roller, organisasjonsnummer, VEILEDER) shouldBe true
            hasRolle(roller, organisasjonsnummer, KOORDINATOR) shouldBe true

            val databasePerson = personRepository.get(norskIdent)
            databasePerson.shouldNotBeNull()
            databasePerson.lastSynchronized.days() shouldBe ZonedDateTime.now().days()

            hasRolleInDatabase(norskIdent, organisasjonsnummer, VEILEDER) shouldBe true
            hasRolleInDatabase(norskIdent, organisasjonsnummer, KOORDINATOR) shouldBe true
        }

        @Test
        fun `lagrer ikke personen når Altinn ikke returnerer roller`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            mockAltinnRoller(norskIdent, listOf(VEILEDER), emptyList())

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)

            // Assert
            verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
            roller.size shouldBe 0
            personRepository.get(norskIdent) shouldBe null
        }

        @Test
        fun `returnerer lagrede roller uten å kontakte Altinn ved fersk synkronisering`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val personDbo = personRepository.createAndSetSynchronized(norskIdent)
            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(VEILEDER, organisasjonsnummer)),
            )

            // Act
            rolleService.getRollerForPerson(norskIdent)

            // Assert
            verify(exactly = 0) { altinnClient.hentRoller(any(), any()) }
        }

        @Test
        fun `henter roller fra Altinn når personen ikke har lagrede roller`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            personRepository.createAndSetSynchronized(norskIdent)
            mockAltinnRoller(norskIdent, listOf(KOORDINATOR, VEILEDER), listOf(organisasjonsnummer))

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)

            // Assert
            hasRolle(roller, organisasjonsnummer, VEILEDER) shouldBe true
            hasRolle(roller, organisasjonsnummer, KOORDINATOR) shouldBe true
            verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
        }

        @Test
        fun `ugyldiggjør roller som er fjernet i Altinn`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val personDbo = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(KOORDINATOR, organisasjonsnummer)),
            )

            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(VEILEDER, organisasjonsnummer)),
            )

            mockAltinnRoller(norskIdent, listOf(KOORDINATOR), listOf(organisasjonsnummer))

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)

            // Assert
            hasRolle(roller, organisasjonsnummer, VEILEDER) shouldBe false
            hasRolle(roller, organisasjonsnummer, KOORDINATOR) shouldBe true

            isRolleGyldig(personDbo.id, organisasjonsnummer, VEILEDER) shouldBe false
        }

        @Test
        fun `legger til roller som er gitt i Altinn`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val personDbo = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(VEILEDER, organisasjonsnummer)),
            )
            mockAltinnRoller(norskIdent, listOf(KOORDINATOR, VEILEDER), listOf(organisasjonsnummer))

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)

            // Assert
            hasRolle(roller, organisasjonsnummer, VEILEDER) shouldBe true
            hasRolle(roller, organisasjonsnummer, KOORDINATOR) shouldBe true
        }

        @Test
        fun `oppretter ny historikkrad når tilgangen gis på nytt`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val personDbo = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(KOORDINATOR, organisasjonsnummer)),
            )
            mockAltinnRoller(norskIdent, listOf(KOORDINATOR, VEILEDER), emptyList())

            // Act
            val rollerUtenTilgang = rolleService.getRollerForPerson(norskIdent)

            // Assert
            rollerUtenTilgang.isEmpty() shouldBe true

            // Arrange
            mockAltinnRoller(norskIdent, listOf(KOORDINATOR), listOf(organisasjonsnummer))

            // Act
            val rollerEtterGjenoppretting = rolleService.getRollerForPerson(norskIdent)

            // Assert
            hasRolle(rollerEtterGjenoppretting, organisasjonsnummer, KOORDINATOR) shouldBe true
            val databaseRoller = jdbcTemplate.query(
                """
                SELECT valid_to IS NULL
                FROM rolle
                WHERE person_id = ? AND rolle = ?
                ORDER BY id
                """.trimIndent(),
                { rs, _ -> rs.getBoolean(1) },
                personDbo.id,
                KOORDINATOR.toString(),
            )
            databaseRoller shouldBe listOf(false, true)
        }

        @Test
        fun `beholder lagrede roller når Altinn ikke er tilgjengelig`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val personDbo = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = personDbo.id,
                rolleOgOrganisasjonsnummerSett = setOf(Pair(KOORDINATOR, organisasjonsnummer)),
            )
            every { altinnClient.hentRoller(norskIdent, RolleType.entries) } throws
                RuntimeException("Klarte ikke å hente organisasjoner code=500")

            // Act
            val roller = rolleService.getRollerForPerson(norskIdent)
            val updatedPerson = personRepository.get(norskIdent)

            // Assert
            verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
            hasRolle(roller, organisasjonsnummer, KOORDINATOR) shouldBe true
            val synchronizedPerson = updatedPerson.shouldNotBeNull()
            synchronizedPerson.lastSynchronized.days() shouldNotBe ZonedDateTime.now().days()
        }
    }

    @Nested
    inner class SynkroniserRoller {
        @Test
        fun `beholder nyeste vellykkede resultat ved samtidige synkroniseringer`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummerA = UUID.randomUUID().toString()
            val organisasjonsnummerB = UUID.randomUUID().toString()
            val person = opprettUsynkronisertPerson(norskIdent)
            val firstCallStarted = CountDownLatch(1)
            val releaseFirstCall = CountDownLatch(1)
            val secondCallStarted = CountDownLatch(1)
            val callNumber = AtomicInteger()
            val executor = Executors.newFixedThreadPool(2)

            every { altinnClient.hentRoller(norskIdent, RolleType.entries) } answers {
                val organisasjonsnummer = when (callNumber.incrementAndGet()) {
                    1 -> {
                        firstCallStarted.countDown()
                        releaseFirstCall.await(5, TimeUnit.SECONDS) shouldBe true
                        organisasjonsnummerA
                    }

                    2 -> {
                        secondCallStarted.countDown()
                        organisasjonsnummerB
                    }

                    else -> error("Forventet bare to Altinn-kall")
                }

                RolleType.entries.associateWith { rolleType ->
                    if (rolleType == VEILEDER) listOf(organisasjonsnummer) else emptyList()
                }
            }

            try {
                // Act
                val firstSynchronization = executor.submit {
                    rolleService.updateRollerFromAltinn(person.id, norskIdent)
                }
                firstCallStarted.await(5, TimeUnit.SECONDS) shouldBe true

                val secondSynchronization = executor.submit {
                    rolleService.updateRollerFromAltinn(person.id, norskIdent)
                }

                // Assert
                secondCallStarted.await(5, TimeUnit.SECONDS) shouldBe true
                secondSynchronization.get(5, TimeUnit.SECONDS)
                releaseFirstCall.countDown()
                firstSynchronization.get(5, TimeUnit.SECONDS)

                hentAktiveOrganisasjonsnumre(person.id, VEILEDER) shouldBe listOf(organisasjonsnummerB)
                personRepository.get(norskIdent).shouldNotBeNull().appliedSynchronizationAttempt shouldBe 2
            } finally {
                releaseFirstCall.countDown()
                executor.shutdownNow()
            }
        }

        @Test
        fun `bruker eldre vellykket resultat når nyere synkronisering feiler`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummer = UUID.randomUUID().toString()
            val person = opprettUsynkronisertPerson(norskIdent)
            val firstCallStarted = CountDownLatch(1)
            val releaseFirstCall = CountDownLatch(1)
            val secondCallStarted = CountDownLatch(1)
            val callNumber = AtomicInteger()
            val executor = Executors.newFixedThreadPool(2)

            every { altinnClient.hentRoller(norskIdent, RolleType.entries) } answers {
                when (callNumber.incrementAndGet()) {
                    1 -> {
                        firstCallStarted.countDown()
                        releaseFirstCall.await(5, TimeUnit.SECONDS) shouldBe true
                        RolleType.entries.associateWith { rolleType ->
                            if (rolleType == VEILEDER) listOf(organisasjonsnummer) else emptyList()
                        }
                    }

                    2 -> {
                        secondCallStarted.countDown()
                        throw RuntimeException("Altinn er utilgjengelig")
                    }

                    else -> error("Forventet bare to Altinn-kall")
                }
            }

            try {
                // Act
                val firstSynchronization = executor.submit {
                    rolleService.updateRollerFromAltinn(person.id, norskIdent)
                }
                firstCallStarted.await(5, TimeUnit.SECONDS) shouldBe true

                val secondSynchronization = executor.submit {
                    rolleService.updateRollerFromAltinn(person.id, norskIdent)
                }
                secondCallStarted.await(5, TimeUnit.SECONDS) shouldBe true
                secondSynchronization.get(5, TimeUnit.SECONDS)
                releaseFirstCall.countDown()
                firstSynchronization.get(5, TimeUnit.SECONDS)

                // Assert
                hentAktiveOrganisasjonsnumre(person.id, VEILEDER) shouldBe listOf(organisasjonsnummer)
                assertSoftly(personRepository.get(norskIdent).shouldNotBeNull()) {
                    synchronizationAttempt shouldBe 2
                    appliedSynchronizationAttempt shouldBe 1
                }
            } finally {
                releaseFirstCall.countDown()
                executor.shutdownNow()
            }
        }

        @Test
        fun `ruller tilbake ugyldiggjøring og synkroniseringstidspunkt når innsetting av rolle feiler`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val gammelOrganisasjon = UUID.randomUUID().toString()
            val nyOrganisasjon = UUID.randomUUID().toString()
            val person = opprettUsynkronisertPerson(norskIdent)
            val lastSynchronized = personRepository.get(norskIdent).shouldNotBeNull().lastSynchronized
            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = setOf(KOORDINATOR to gammelOrganisasjon),
            )
            mockAltinnRoller(norskIdent, listOf(VEILEDER), listOf(nyOrganisasjon))
            jdbcTemplate.execute(
                "ALTER TABLE rolle ADD CONSTRAINT test_reject_role_insert CHECK (organisasjonsnummer <> '$nyOrganisasjon')",
            )

            try {
                // Act
                shouldThrow<DataIntegrityViolationException> {
                    rolleService.updateRollerFromAltinn(person.id, norskIdent)
                }

                // Assert
                isRolleGyldig(person.id, gammelOrganisasjon, KOORDINATOR) shouldBe true
                hentAktiveOrganisasjonsnumre(person.id, VEILEDER) shouldBe emptyList()
                personRepository.get(norskIdent).shouldNotBeNull().lastSynchronized shouldBe lastSynchronized
            } finally {
                jdbcTemplate.execute("ALTER TABLE rolle DROP CONSTRAINT test_reject_role_insert")
            }
        }

        @Test
        fun `legger til nye organisasjoner og fjerner utdaterte for samme rolle`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummerA = UUID.randomUUID().toString()
            val organisasjonsnummerB = UUID.randomUUID().toString()
            val organisasjonsnummerC = UUID.randomUUID().toString()
            val person = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = setOf(
                    VEILEDER to organisasjonsnummerA,
                    VEILEDER to organisasjonsnummerC,
                ),
            )
            mockAltinnRoller(
                norskIdent,
                listOf(VEILEDER),
                listOf(organisasjonsnummerA, organisasjonsnummerB),
            )

            // Act
            rolleService.getRollerForPerson(norskIdent)

            // Assert
            val aktiveOrgnr = hentAktiveOrganisasjonsnumre(person.id, VEILEDER)
            aktiveOrgnr.size shouldBe 2
            aktiveOrgnr.toSet() shouldBe setOf(organisasjonsnummerA, organisasjonsnummerB)
            isRolleGyldig(person.id, organisasjonsnummerC, VEILEDER) shouldBe false
        }

        @Test
        fun `legger ikke til organisasjoner som allerede har aktiv rolle`() {
            // Arrange
            val norskIdent = UUID.randomUUID().toString()
            val organisasjonsnummerA = UUID.randomUUID().toString()
            val organisasjonsnummerB = UUID.randomUUID().toString()
            val person = opprettUsynkronisertPerson(norskIdent)
            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = setOf(VEILEDER to organisasjonsnummerA),
            )
            mockAltinnRoller(
                norskIdent,
                listOf(VEILEDER),
                listOf(organisasjonsnummerA, organisasjonsnummerB),
            )

            // Act
            rolleService.getRollerForPerson(norskIdent)

            // Assert
            val aktiveOrgnr = hentAktiveOrganisasjonsnumre(person.id, VEILEDER)
            aktiveOrgnr.size shouldBe 2
            aktiveOrgnr.toSet() shouldBe setOf(organisasjonsnummerA, organisasjonsnummerB)
        }
    }

    private fun hasRolleInDatabase(
        norskIdent: String,
        organisasjonsnummerNumber: String,
        rolle: RolleType,
    ): Boolean = rolleRepository
        .hentGyldigeRollerForPerson(norskIdent)
        .any { it.organisasjonsnummer == organisasjonsnummerNumber && it.rolleType == rolle }

    private fun opprettUsynkronisertPerson(norskIdent: String) = personRepository.createAndSetSynchronized(
        norskIdent,
        Instant.EPOCH.atZone(ZoneOffset.UTC),
    )

    private fun hentAktiveOrganisasjonsnumre(
        personId: Long,
        rolle: RolleType,
    ): List<String> = jdbcTemplate.query(
        """
        SELECT organisasjonsnummer
        FROM rolle
        WHERE person_id = ? AND rolle = ? AND valid_to IS NULL
        """.trimIndent(),
        { rs, _ -> rs.getString("organisasjonsnummer") },
        personId,
        rolle.toString(),
    )

    private fun isRolleGyldig(
        personId: Long,
        organisasjonsnummer: String,
        rolle: RolleType,
    ): Boolean = jdbcTemplate
        .query(
            """
            SELECT valid_to IS NULL
            FROM rolle
            WHERE person_id = ? AND organisasjonsnummer = ? AND rolle = ?
            """.trimIndent(),
            { rs, _ -> rs.getBoolean(1) },
            personId,
            organisasjonsnummer,
            rolle.toString(),
        ).single()

    private fun hasRolle(
        list: List<RollerIOrganisasjon>,
        organisasjonsnummerNumber: String,
        rolle: RolleType,
    ): Boolean = list
        .find { it.organisasjonsnummer == organisasjonsnummerNumber }
        ?.roller
        ?.find { it.rolleType == rolle } != null

    private fun ZonedDateTime.days(): ZonedDateTime = this.truncatedTo(ChronoUnit.DAYS)
}
