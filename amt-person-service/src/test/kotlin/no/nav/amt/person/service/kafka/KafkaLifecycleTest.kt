package no.nav.amt.person.service.kafka

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verifyOrder
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import org.junit.jupiter.api.Test

class KafkaLifecycleTest {
    private val consumerClient = mockk<KafkaConsumerClient>(relaxUnitFun = true)
    private val consumerRecordProcessor = mockk<KafkaConsumerRecordProcessor>(relaxUnitFun = true)
    private val producerRecordProcessor = mockk<KafkaProducerRecordProcessor>(relaxUnitFun = true)
    private val lifecycle = KafkaConsumerLifecycle(
        client = consumerClient,
        consumerRecordProcessor = consumerRecordProcessor,
        producerRecordProcessor = producerRecordProcessor,
    )

    @Test
    fun `starter og stopper Kafka-klienter og record-processorer i kontrollert rekkefølge`() {
        // Arrange
        lifecycle.isRunning shouldBe false

        // Act
        lifecycle.start()
        val runningAfterStart = lifecycle.isRunning
        lifecycle.stop()

        // Assert
        runningAfterStart shouldBe true
        lifecycle.isRunning shouldBe false
        verifyOrder {
            consumerClient.start()
            consumerRecordProcessor.start()
            producerRecordProcessor.start()
            producerRecordProcessor.close()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }
}
