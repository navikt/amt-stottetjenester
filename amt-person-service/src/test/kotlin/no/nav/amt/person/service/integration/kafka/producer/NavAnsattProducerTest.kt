package no.nav.amt.person.service.integration.kafka.producer

import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.kafka.producer.dto.NavAnsattDtoV1
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navansatt.NavAnsattUpdater
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class NavAnsattProducerTest(
    private val kafkaProducerService: KafkaProducerService,
    private val navAnsattService: NavAnsattService,
    private val navAnsattUpdater: NavAnsattUpdater,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val transactionTemplate: TransactionTemplate,
) : IntegrationTestBase() {
    @Test
    fun `publiserNavAnsatt - skal publisere ansatt med riktig key og value`() {
        // Arrange
        val ansatt = TestData.lagNavAnsatt()

        // Act
        transactionTemplate.executeWithoutResult { kafkaProducerService.publiserNavAnsatt(ansatt) }

        // Assert
        val record = outboxRecords().single()
        val forventetValue = ansattTilV1Json(ansatt)

        record.topic shouldBe kafkaTopicProperties.amtNavAnsattPersonaliaTopic
        record.key shouldBe ansatt.id.toString()
        record.value shouldBe forventetValue
    }

    @Test
    fun `publiserNavAnsatt - ansatt er oppdatert - skal publisere ny melding`() {
        // Arrange
        val ansatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(ansatt)
        val oppdatertAnsatt = ansatt.copy(
            navn = "nytt navn",
            telefon = "nytt nummer",
            epost = "ny@epost.no",
        )

        // Act
        navAnsattService.upsert(oppdatertAnsatt)

        // Assert
        val record = outboxRecords().single()
        val forventetValue = ansattTilV1Json(oppdatertAnsatt)

        record.key shouldBe ansatt.id.toString()
        record.value shouldBe forventetValue
    }

    @Test
    fun `publiserNavAnsatt - flere ansatte sjekkes for oppdatering - skal publisere melding kun for de med endring`() {
        // Arrange
        val endretAnsatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(endretAnsatt)

        val uendretAnsatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(uendretAnsatt)

        every { nomClient.hentNavAnsatte(any()) } returns listOf(
            NomNavAnsatt(
                navIdent = endretAnsatt.navIdent,
                navn = "nytt navn",
                telefonnummer = endretAnsatt.telefon,
                epost = endretAnsatt.epost,
                orgTilknytning = TestData.orgTilknytning,
            ),
            NomNavAnsatt(
                navIdent = uendretAnsatt.navIdent,
                navn = uendretAnsatt.navn,
                telefonnummer = uendretAnsatt.telefon,
                epost = uendretAnsatt.epost,
                orgTilknytning = TestData.orgTilknytning,
            ),
        )

        // Act
        navAnsattUpdater.oppdaterAlle()

        // Assert
        val records = outboxRecords()
        records.size shouldBe 1
        records.single().key shouldBe endretAnsatt.id.toString()
    }

    private fun ansattTilV1Json(ansatt: NavAnsattDbo): String = objectMapper.writeValueAsString(
        NavAnsattDtoV1(
            id = ansatt.id,
            navident = ansatt.navIdent,
            navn = ansatt.navn,
            telefon = ansatt.telefon,
            epost = ansatt.epost,
            navEnhetId = ansatt.navEnhetId,
        ),
    )
}
