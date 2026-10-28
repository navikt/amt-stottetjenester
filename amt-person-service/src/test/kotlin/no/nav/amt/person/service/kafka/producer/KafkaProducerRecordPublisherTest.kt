package no.nav.amt.person.service.kafka.producer

import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class KafkaProducerRecordPublisherTest {
    private val producerClient = mockk<KafkaProducerClient<ByteArray, ByteArray>>()
    private val producer = mockk<Producer<ByteArray, ByteArray>>(relaxUnitFun = true)
    private val publisher = BatchedKafkaProducerRecordPublisher(producerClient)
    private val record = StoredProducerRecord(
        1L,
        "topic",
        "key".toByteArray(),
        "value".toByteArray(),
        "[]",
    )

    @Nested
    inner class PublishStoredRecords {
        @Test
        fun `bekreftet publisering markerer raden for sletting`() {
            // Arrange
            every { producerClient.getProducer() } returns producer
            every { producerClient.send(any(), any()) } answers {
                secondArg<Callback>().onCompletion(mockk<RecordMetadata>(), null)
                CompletableFuture.completedFuture(mockk<RecordMetadata>())
            }

            // Act
            val idsToDelete = publisher.publishStoredRecords(listOf(record))

            // Assert
            idsToDelete shouldContainExactly listOf(record.id)
            verify(exactly = 1) { producer.flush() }
        }

        @Test
        fun `publiseringsfeil beholder raden for nytt forsøk`() {
            // Arrange
            every { producerClient.getProducer() } returns producer
            every { producerClient.send(any(), any()) } answers {
                secondArg<Callback>().onCompletion(null, RuntimeException("Kafka er utilgjengelig"))
                CompletableFuture.completedFuture(mockk<RecordMetadata>())
            }

            // Act
            val idsToDelete = publisher.publishStoredRecords(listOf(record))

            // Assert
            idsToDelete shouldContainExactly emptyList()
            verify(exactly = 1) { producer.flush() }
        }
    }
}
