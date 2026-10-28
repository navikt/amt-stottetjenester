package no.nav.arrangor.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.arrangor.MetricsService
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.KafkaProducerRecordPublisher
import org.junit.jupiter.api.Test

class MetricsKafkaProducerRecordPublisherTest {
    private val delegate = mockk<KafkaProducerRecordPublisher>()
    private val metricsService = mockk<MetricsService>(relaxUnitFun = true)
    private val publisher = MetricsKafkaProducerRecordPublisher(delegate, metricsService)

    @Test
    fun `teller bare meldinger som produsenten bekrefter`() {
        // Arrange
        val records = listOf(
            storedRecord(id = 1L, topic = ANSATT_TOPIC),
            storedRecord(id = 2L, topic = ARRANGOR_TOPIC),
            storedRecord(id = 3L, topic = ANSATT_TOPIC),
        )
        every { delegate.publishStoredRecords(records) } returns listOf(1L, 2L)

        // Act
        val publishedIds = publisher.publishStoredRecords(records)

        // Assert
        publishedIds shouldBe listOf(1L, 2L)
        verify(exactly = 1) { metricsService.incPubliserteAnsatte(count = 1) }
        verify(exactly = 1) { metricsService.incPubliserteArrangorer(count = 1) }
    }

    @Test
    fun `teller ikke meldinger når publisering feiler`() {
        // Arrange
        val records = listOf(storedRecord(id = 1L, topic = ANSATT_TOPIC))
        every { delegate.publishStoredRecords(records) } throws IllegalStateException("Kafka unavailable")

        // Act & Assert
        shouldThrow<IllegalStateException> {
            publisher.publishStoredRecords(records)
        }
        verify(exactly = 0) { metricsService.incPubliserteAnsatte(any()) }
        verify(exactly = 0) { metricsService.incPubliserteArrangorer(any()) }
    }

    private fun storedRecord(
        id: Long,
        topic: String,
    ) = StoredProducerRecord(
        id,
        topic,
        byteArrayOf(),
        byteArrayOf(),
        "[]",
    )
}
