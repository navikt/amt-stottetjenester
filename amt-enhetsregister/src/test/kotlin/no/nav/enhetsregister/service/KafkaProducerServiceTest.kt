package no.nav.enhetsregister.service

import no.nav.enhetsregister.kafka.KafkaTopicProperties
import no.nav.enhetsregister.kafka.VirksomhetV1Dto
import no.nav.enhetsregister.testutils.IntegrationTest
import no.nav.enhetsregister.testutils.KafkaMessageConsumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class KafkaProducerServiceTest(
    private val kafkaProducerService: KafkaProducerService,
    private val kafkaTopicProperties: KafkaTopicProperties,
) : IntegrationTest() {
    @Test
    fun `skal publisere virksomhet pa topic`() {
        val virksomhetV1Dto = VirksomhetV1Dto(
            organisasjonsnummer = "123456789",
            navn = "Orgnavn",
            overordnetEnhetOrganisasjonsnummer = "987654321",
        )

        kafkaProducerService.publiserVirksomhet(virksomhetV1Dto)

        await().untilAsserted {
            val records: ConsumerRecords<String, String>? =
                KafkaMessageConsumer.consume(kafkaTopicProperties.virksomheterTopic)

            assertNotNull(records)

            val record = records?.first()
            assertEquals(virksomhetV1Dto.organisasjonsnummer, record?.key().toString())

            val expectedJson =
                """{"organisasjonsnummer":"123456789","navn":"Orgnavn","overordnetEnhetOrganisasjonsnummer":"987654321"}""".trimIndent()
            assertEquals(record?.value(), expectedJson)
        }
    }
}
