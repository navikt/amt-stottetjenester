package no.nav.amt.person.service.integration.kafka.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.clients.norg.toNorgNavEnhetDto
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.PersonUpdateEvent
import no.nav.amt.person.service.person.model.Rolle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate

class KafkaOutboxTransactionTest(
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val personService: PersonService,
    private val transactionTemplate: TransactionTemplate,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kafkaProducerService: KafkaProducerService,
) : IntegrationTestBase() {
    @Test
    fun `nav-bruker og outbox-rad rulles tilbake sammen`() {
        // Arrange
        val navBruker = TestData.lagNavBruker(
            navVeileder = null,
            navEnhet = null,
        )
        testDataRepository.insertPerson(navBruker.person)

        // Act
        transactionTemplate.executeWithoutResult {
            navBrukerService.upsert(navBruker)
            it.setRollbackOnly()
        }

        // Assert
        template.jdbcTemplate.queryForObject<Int>(
            "SELECT count(*) FROM nav_bruker WHERE id = ?",
            navBruker.id,
        ) shouldBe 0
        outboxRecords() shouldBe emptyList()
    }

    @Test
    fun `nav-ansatt og outbox-rad rulles tilbake sammen`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt().copy(navEnhetId = null)

        // Act
        transactionTemplate.executeWithoutResult {
            navAnsattService.upsert(navAnsatt)
            it.setRollbackOnly()
        }

        // Assert
        template.jdbcTemplate.queryForObject<Int>(
            "SELECT count(*) FROM nav_ansatt WHERE id = ?",
            navAnsatt.id,
        ) shouldBe 0
        outboxRecords() shouldBe emptyList()
    }

    @Test
    fun `batch med nav-ansatte og outbox-rader rulles tilbake sammen`() {
        // Arrange
        val ansatte = setOf(
            TestData.lagNavAnsatt().copy(navEnhetId = null),
            TestData.lagNavAnsatt().copy(navEnhetId = null),
        )

        // Act
        transactionTemplate.executeWithoutResult {
            navAnsattService.upsertMany(ansatte)
            it.setRollbackOnly()
        }

        // Assert
        template.jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM nav_ansatt") shouldBe 0
        outboxRecords() shouldBe emptyList()
    }

    @Test
    fun `nav-enhet og outbox-rad rulles tilbake sammen`() {
        // Arrange
        val navEnhet = TestData.lagNavEnhet()
        every { norgClient.hentNavEnhet(navEnhet.enhetId) } returns navEnhet.toNorgNavEnhetDto()

        // Act
        transactionTemplate.executeWithoutResult {
            navEnhetService.hentEllerOpprettNavEnhet(navEnhet.enhetId)
            it.setRollbackOnly()
        }

        // Assert
        template.jdbcTemplate.queryForObject<Int>(
            "SELECT count(*) FROM nav_enhet WHERE nav_enhet_id = ?",
            navEnhet.enhetId,
        ) shouldBe 0
        outboxRecords() shouldBe emptyList()
    }

    @Test
    fun `nav-enhetsoppdatering og outbox-rad rulles tilbake sammen`() {
        // Arrange
        val navEnhet = TestData.lagNavEnhet()
        testDataRepository.insertNavEnhet(navEnhet)
        every { norgClient.hentNavEnheter(listOf(navEnhet.enhetId)) } returns
            listOf(navEnhet.copy(navn = "Oppdatert").toNorgNavEnhetDto())

        // Act
        transactionTemplate.executeWithoutResult {
            navEnhetService.oppdaterNavEnheter(listOf(navEnhet))
            it.setRollbackOnly()
        }

        // Assert
        template.jdbcTemplate.queryForObject<String>(
            "SELECT navn FROM nav_enhet WHERE id = ?",
            navEnhet.id,
        ) shouldBe navEnhet.navn
        outboxRecords() shouldBe emptyList()
    }

    @Nested
    inner class PersonServiceOppdaterNavn {
        @Test
        fun `arrangor-ansatt og lytterens outbox-rad rulles tilbake sammen`() {
            // Arrange
            val person = TestData.lagPerson()
            testDataRepository.insertPerson(person)
            testDataRepository.insertRolle(person.id, Rolle.ARRANGOR_ANSATT)

            // Act
            every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(person.copy(fornavn = "Oppdatert"))
            transactionTemplate.executeWithoutResult {
                personService.oppdaterNavn(person)
                it.setRollbackOnly()
            }

            // Assert
            template.jdbcTemplate.queryForObject<String>(
                "SELECT fornavn FROM person WHERE id = ?",
                person.id,
            ) shouldBe person.fornavn
            outboxRecords() shouldBe emptyList()
        }

        @Test
        fun `nav-bruker og lytterens outbox-rad rulles tilbake sammen`() {
            // Arrange
            val navBruker = TestData.lagNavBruker()
            testDataRepository.insertNavBruker(navBruker)

            // Act
            every {
                pdlClient.hentPerson(navBruker.person.personident)
            } returns TestData.lagPdlPerson(navBruker.person.copy(fornavn = "Oppdatert"))
            transactionTemplate.executeWithoutResult {
                personService.oppdaterNavn(navBruker.person)
                it.setRollbackOnly()
            }

            // Assert
            template.jdbcTemplate.queryForObject<String>(
                "SELECT fornavn FROM person WHERE id = ?",
                navBruker.person.id,
            ) shouldBe navBruker.person.fornavn
            outboxRecords() shouldBe emptyList()
        }
    }

    @Test
    fun `PersonUpdateEvent uten transaksjon - kaster og lagrer ingen outbox-rad`() {
        // Arrange
        val navBruker = TestData.lagNavBruker()
        testDataRepository.insertNavBruker(navBruker)
        testDataRepository.insertRolle(navBruker.person.id, Rolle.ARRANGOR_ANSATT)

        // Act & Assert
        shouldThrow<IllegalTransactionStateException> {
            applicationEventPublisher.publishEvent(PersonUpdateEvent(navBruker.person))
        }
        outboxRecords() shouldBe emptyList()
    }

    @Test
    fun `KafkaProducerService uten transaksjon - kaster og lagrer ingen outbox-rad`() {
        // Act & Assert
        shouldThrow<IllegalTransactionStateException> {
            kafkaProducerService.publiserNavEnhet(TestData.lagNavEnhet())
        }
        outboxRecords() shouldBe emptyList()
    }
}
