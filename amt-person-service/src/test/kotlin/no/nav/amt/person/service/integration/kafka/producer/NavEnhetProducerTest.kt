package no.nav.amt.person.service.integration.kafka.producer

import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.kafka.producer.dto.NavEnhetDtoV1
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class NavEnhetProducerTest(
    private val kafkaProducerService: KafkaProducerService,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val transactionTemplate: TransactionTemplate,
) : IntegrationTestBase() {
    @Test
    fun `publiserNavEnhet - skal publisere enhet med riktig key og value`() {
        // Arrange
        val navEnhet = TestData.lagNavEnhet()

        // Act
        transactionTemplate.executeWithoutResult { kafkaProducerService.publiserNavEnhet(navEnhet) }

        // Assert
        val record = outboxRecords().single()
        val forventetValue = objectMapper.writeValueAsString(NavEnhetDtoV1.fromDbo(navEnhet))

        record.topic shouldBe kafkaTopicProperties.amtNavEnhetTopic
        record.key shouldBe navEnhet.id.toString()
        record.value shouldBe forventetValue
    }
}
