package no.nav.amt.person.service.integration.kafka.producer

import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.kafka.producer.dto.ArrangorAnsattDtoV1
import no.nav.amt.person.service.person.ArrangorAnsattService
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.model.Rolle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class ArrangorAnsattProducerTest(
    private val kafkaProducerService: KafkaProducerService,
    private val personService: PersonService,
    private val arrangorAnsattService: ArrangorAnsattService,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val transactionTemplate: TransactionTemplate,
) : IntegrationTestBase() {
    @Test
    fun `publiserArrangorAnsatt - skal publisere ansatt med riktig key og value`() {
        // Arrange
        val ansatt = TestData.lagPerson()

        // Act
        transactionTemplate.executeWithoutResult { kafkaProducerService.publiserArrangorAnsatt(ansatt) }

        // Assert
        val record = outboxRecords().single()
        val forventetValue = objectMapper.writeValueAsString(ArrangorAnsattDtoV1.fromDbo(ansatt))

        record.topic shouldBe kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic
        record.key shouldBe ansatt.id.toString()
        record.value shouldBe forventetValue
    }

    @Nested
    inner class PersonServiceOppdaterNavn {
        @Test
        fun `person oppdateres - skal publisere ansatt med riktig key og value`() {
            // Arrange
            val ansatt = TestData.lagPerson()
            testDataRepository.insertPerson(ansatt)
            testDataRepository.insertRolle(ansatt.id, Rolle.ARRANGOR_ANSATT)
            val oppdatertAnsatt = ansatt.copy(
                fornavn = "Nytt",
                mellomnavn = null,
                etternavn = "Navn",
            )

            // Act
            every { pdlClient.hentPerson(ansatt.personident) } returns TestData.lagPdlPerson(oppdatertAnsatt)
            personService.oppdaterNavn(ansatt)

            // Assert
            val record = outboxRecords().single()
            val forventetValue = objectMapper.writeValueAsString(ArrangorAnsattDtoV1.fromDbo(oppdatertAnsatt))
            record.key shouldBe ansatt.id.toString()
            record.value shouldBe forventetValue
        }

        @Test
        fun `person er ikke arrangor-ansatt - skal ikke publiseres`() {
            // Arrange
            val navBruker = TestData.lagPerson()
            testDataRepository.insertPerson(navBruker)
            testDataRepository.insertRolle(navBruker.id, Rolle.NAV_BRUKER)
            val oppdaterNavBruker = navBruker.copy(
                fornavn = "Nytt",
                mellomnavn = null,
                etternavn = "Navn",
            )

            // Act
            every { pdlClient.hentPerson(navBruker.personident) } returns TestData.lagPdlPerson(oppdaterNavBruker)
            personService.oppdaterNavn(navBruker)

            // Assert
            outboxRecords().none { it.topic == kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic } shouldBe true
        }

        @Test
        fun `person har flere roller - skal publiseres`() {
            // Arrange
            val navBruker = TestData.lagNavBruker()
            testDataRepository.insertNavBruker(navBruker)
            val person = navBruker.person
            testDataRepository.insertRolle(person.id, Rolle.ARRANGOR_ANSATT)
            val oppdatertPerson = person.copy(
                fornavn = "Nytt",
                mellomnavn = null,
                etternavn = "Navn",
            )

            // Act
            every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(oppdatertPerson)
            personService.oppdaterNavn(person)

            // Assert
            outboxRecords().any {
                it.topic == kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic && it.key == person.id.toString()
            } shouldBe true
        }
    }

    // Rolletildeling skal ikke gi melding: amt-arrangor får personalia i API-svaret.
    // Se KDoc på ArrangorAnsattService.hentEllerOpprettAnsatt.
    @Test
    fun `hentEllerOpprettAnsatt - ny person - skal ikke publisere ansatt`() {
        // Arrange
        val person = TestData.lagPerson()
        every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(person)

        // Act
        arrangorAnsattService.hentEllerOpprettAnsatt(person.personident)

        // Assert
        outboxRecords().none { it.topic == kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic } shouldBe true
    }
}
