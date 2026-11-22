package no.nav.amt.person.service.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
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
            producerRecordProcessor.closeAndAwait()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }

    @Test
    fun `stopper alle Kafka-komponenter selv om flere stoppkall feiler`() {
        val producerFailure = IllegalStateException("Simulert producer-lukkefeil")
        val recordProcessorFailure = IllegalStateException("Simulert record-processor-stoppfeil")
        val consumerFailure = IllegalStateException("Simulert consumer-stoppfeil")
        every { producerRecordProcessor.closeAndAwait() } throws producerFailure
        every { consumerRecordProcessor.stop() } throws recordProcessorFailure
        every { consumerClient.stop() } throws consumerFailure
        lifecycle.start()

        shouldThrow<IllegalStateException> { lifecycle.stop() } shouldBe producerFailure

        lifecycle.isRunning shouldBe false
        producerFailure.suppressed.toList() shouldBe listOf(recordProcessorFailure, consumerFailure)
        verifyOrder {
            producerRecordProcessor.closeAndAwait()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }

    @Test
    fun `beholder samme exception uten self-suppression og stopper alle komponentene`() {
        val failure = IllegalStateException("Simulert felles stoppfeil")
        every { producerRecordProcessor.closeAndAwait() } throws failure
        every { consumerRecordProcessor.stop() } throws failure
        every { consumerClient.stop() } throws failure
        lifecycle.start()

        shouldThrow<IllegalStateException> { lifecycle.stop() } shouldBe failure

        lifecycle.isRunning shouldBe false
        failure.suppressed.toList() shouldBe emptyList()
        verifyOrder {
            producerRecordProcessor.closeAndAwait()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }
}
