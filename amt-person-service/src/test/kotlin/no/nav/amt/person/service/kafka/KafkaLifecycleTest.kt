package no.nav.amt.person.service.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxRuntime
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import org.junit.jupiter.api.Test

class KafkaLifecycleTest {
    private val consumerClient = mockk<KafkaConsumerClient>(relaxUnitFun = true)
    private val consumerRecordProcessor = mockk<KafkaConsumerRecordProcessor>(relaxUnitFun = true)
    private val outboxRuntime = mockk<KafkaOutboxRuntime>(relaxUnitFun = true)
    private val lifecycle = KafkaConsumerLifecycle(
        client = consumerClient,
        consumerRecordProcessor = consumerRecordProcessor,
        outboxRuntime = outboxRuntime,
    )

    @Test
    fun `starter og stopper Kafka-klienter og record-processorer i kontrollert rekkefølge`() {
        // Arrange
        lifecycle.isRunning shouldBe false

        // Act
        lifecycle.stop()
        lifecycle.start()
        lifecycle.start()
        val runningAfterStart = lifecycle.isRunning
        lifecycle.stop()
        lifecycle.stop()

        // Assert
        runningAfterStart shouldBe true
        lifecycle.isRunning shouldBe false
        verifyOrder {
            consumerClient.start()
            consumerRecordProcessor.start()
            outboxRuntime.start()
            outboxRuntime.stop()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
        verify(exactly = 1) { outboxRuntime.start() }
        verify(exactly = 1) { outboxRuntime.stop() }
    }

    @Test
    fun `oppstartsfeil stopper consumer-prosessor og klient i omvendt rekkefølge`() {
        val failure = IllegalStateException("Outbox startup failed")
        every { outboxRuntime.start() } throws failure

        shouldThrow<IllegalStateException> { lifecycle.start() } shouldBe failure

        lifecycle.isRunning shouldBe false
        verifyOrder {
            consumerClient.start()
            consumerRecordProcessor.start()
            outboxRuntime.start()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }

    @Test
    fun `oppryddingsfeil skjuler ikke outbox-oppstartsfeilen`() {
        val failure = IllegalStateException("Outbox startup failed")
        val processorFailure = IllegalStateException("Consumer processor stop failed")
        val clientFailure = IllegalStateException("Consumer client stop failed")
        every { outboxRuntime.start() } throws failure
        every { consumerRecordProcessor.stop() } throws processorFailure
        every { consumerClient.stop() } throws clientFailure

        shouldThrow<IllegalStateException> { lifecycle.start() } shouldBe failure

        failure.suppressed.toList() shouldBe listOf(processorFailure, clientFailure)
    }

    @Test
    fun `avbrutt outbox-oppstart bevarer interrupt-flagget etter opprydding`() {
        every { outboxRuntime.start() } throws InterruptedException("Startup interrupted")

        try {
            shouldThrow<InterruptedException> { lifecycle.start() }
            Thread.currentThread().isInterrupted shouldBe true
            verify(exactly = 1) { consumerRecordProcessor.stop() }
            verify(exactly = 1) { consumerClient.stop() }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `outbox må stoppe før consumere stoppes og stopp kan forsøkes på nytt`() {
        lifecycle.start()
        every { outboxRuntime.stop() } throws IllegalStateException("Outbox stop failed") andThen Unit

        shouldThrow<IllegalStateException> { lifecycle.stop() }
        lifecycle.isRunning shouldBe true
        verify(exactly = 0) { consumerRecordProcessor.stop() }
        verify(exactly = 0) { consumerClient.stop() }

        lifecycle.stop()

        lifecycle.isRunning shouldBe false
        verifyOrder {
            outboxRuntime.stop()
            outboxRuntime.stop()
            consumerRecordProcessor.stop()
            consumerClient.stop()
        }
    }
}
