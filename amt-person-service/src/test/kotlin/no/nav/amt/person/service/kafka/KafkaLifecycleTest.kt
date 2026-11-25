package no.nav.amt.person.service.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.function.Supplier

class KafkaLifecycleTest {
    private val consumerClient = mockk<KafkaConsumerClient>(relaxUnitFun = true)
    private val consumerRecordProcessor = mockk<KafkaConsumerRecordProcessor>(relaxUnitFun = true)
    private val producerRecordProcessor = mockk<KafkaProducerRecordProcessor>(relaxUnitFun = true)
    private val database = mockk<AutoCloseable>(relaxUnitFun = true)
    private val lifecycle = spyk(
        KafkaConsumerLifecycle(
            client = consumerClient,
            consumerRecordProcessor = consumerRecordProcessor,
            producerRecordProcessor = producerRecordProcessor,
        ),
    )

    @Test
    fun `starter og stopper Kafka-klienter og record-processorer i kontrollert rekkefølge`() {
        // Arrange
        val runningBeforeStart = lifecycle.isRunning

        // Act
        lifecycle.start()
        val runningAfterStart = lifecycle.isRunning
        lifecycle.stop()

        // Assert
        runningBeforeStart shouldBe false
        runningAfterStart shouldBe true
        lifecycle.isRunning shouldBe false
        verifyOrder {
            consumerClient.start()
            consumerRecordProcessor.start()
            producerRecordProcessor.start()
            consumerClient.close()
            consumerRecordProcessor.close()
            producerRecordProcessor.closeAndAwait()
        }
    }

    @Test
    fun `stopper alle Kafka-komponenter selv om flere stoppkall feiler`() {
        // Arrange
        val producerFailure = IllegalStateException("Simulert producer-lukkefeil")
        val recordProcessorFailure = IllegalStateException("Simulert record-processor-lukkefeil")
        val consumerFailure = IllegalStateException("Simulert consumer-lukkefeil")
        val callback = mockk<Runnable>(relaxUnitFun = true)
        every { producerRecordProcessor.closeAndAwait() } throws producerFailure
        every { consumerRecordProcessor.close() } throws recordProcessorFailure
        every { consumerClient.close() } throws consumerFailure
        lifecycle.start()

        // Act
        val thrown = shouldThrow<IllegalStateException> { lifecycle.stop(callback) }

        // Assert
        thrown shouldBe consumerFailure
        lifecycle.isRunning shouldBe false
        consumerFailure.suppressed.toList() shouldBe listOf(recordProcessorFailure, producerFailure)
        verifyOrder {
            consumerClient.close()
            consumerRecordProcessor.close()
            producerRecordProcessor.closeAndAwait()
            callback.run()
        }
    }

    @Test
    fun `beholder samme exception uten self-suppression og stopper alle komponentene`() {
        // Arrange
        val failure = IllegalStateException("Simulert felles stoppfeil")
        every { producerRecordProcessor.closeAndAwait() } throws failure
        every { consumerRecordProcessor.close() } throws failure
        every { consumerClient.close() } throws failure
        lifecycle.start()

        // Act
        val thrown = shouldThrow<IllegalStateException> { lifecycle.stop() }

        // Assert
        thrown shouldBe failure
        lifecycle.isRunning shouldBe false
        failure.suppressed.toList() shouldBe emptyList()
        verifyOrder {
            consumerClient.close()
            consumerRecordProcessor.close()
            producerRecordProcessor.closeAndAwait()
        }
    }

    @Test
    fun `Spring lukker Kafka-komponenter som aldri ble startet`() {
        // Arrange
        val context = context(autoStartup = false)

        // Act
        val runningBeforeClose = context.use { lifecycle.isRunning }

        // Assert
        runningBeforeClose shouldBe false
        verify(exactly = 0) { consumerClient.start() }
        verify(exactly = 0) { consumerRecordProcessor.start() }
        verify(exactly = 0) { producerRecordProcessor.start() }
        verify(exactly = 1) { consumerClient.close() }
        verify(exactly = 1) { consumerRecordProcessor.close() }
        verify(exactly = 1) { producerRecordProcessor.closeAndAwait() }
    }

    @Test
    fun `Spring drenerer consumere før producer og database lukkes`() {
        // Arrange
        val context = context(autoStartup = true)

        // Act
        val runningBeforeClose = context.use { lifecycle.isRunning }

        // Assert
        runningBeforeClose shouldBe true
        verifyOrder {
            consumerClient.start()
            consumerRecordProcessor.start()
            producerRecordProcessor.start()
            consumerClient.close()
            consumerRecordProcessor.close()
            producerRecordProcessor.closeAndAwait()
            database.close()
        }
        lifecycle.isRunning shouldBe false
    }

    private fun context(autoStartup: Boolean): AnnotationConfigApplicationContext {
        every { lifecycle.isAutoStartup } returns autoStartup
        val context = AnnotationConfigApplicationContext()

        fun registerResource(
            name: String,
            resource: Any,
            destroyMethod: String = "close",
        ) {
            context.registerBeanDefinition(
                name,
                RootBeanDefinition(resource.javaClass).apply {
                    instanceSupplier = Supplier { resource }
                    destroyMethodName = destroyMethod
                },
            )
        }
        registerResource("database", database)
        registerResource("kafkaConsumerClient", consumerClient)
        registerResource("kafkaConsumerRecordProcessor", consumerRecordProcessor)
        registerResource("kafkaProducerRecordProcessor", producerRecordProcessor, "closeAndAwait")
        context.registerBean("kafkaLifecycle", KafkaConsumerLifecycle::class.java, Supplier { lifecycle })
        context.refresh()
        return context
    }
}
