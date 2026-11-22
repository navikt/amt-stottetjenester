package no.nav.amt.tiltak.test.integration.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.amt.tiltak.kafka.config.KafkaConsumerLifecycle
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.function.Supplier

class KafkaConsumerLifecycleTest {
    private val client = mockk<KafkaConsumerClient>(relaxUnitFun = true)
    private val processor = mockk<KafkaConsumerRecordProcessor>(relaxUnitFun = true)
    private val database = mockk<AutoCloseable>(relaxUnitFun = true)
    private val lifecycle = spyk(KafkaConsumerLifecycle(client, processor))

    @Test
    fun `starter en gang og bekrefter lukking av klient og record-prosessor`() {
        lifecycle.start()
        lifecycle.start()
        lifecycle.stop()
        lifecycle.stop()

        lifecycle.isRunning shouldBe false
        verifyOrder {
            client.start()
            processor.start()
            client.close()
            processor.close()
        }
        verify(exactly = 1) { client.start() }
        verify(exactly = 1) { client.close() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lukker alle komponenter og varsler Spring selv om lukking feiler`(sammeException: Boolean) {
        val failure = IllegalStateException("Simulert lukkefeil")
        val processorFailure = if (sammeException) failure else IllegalStateException("Simulert prosessor-lukkefeil")
        val callback = mockk<Runnable>(relaxUnitFun = true)
        every { client.close() } throws failure
        every { processor.close() } throws processorFailure
        lifecycle.start()

        shouldThrow<IllegalStateException> { lifecycle.stop(callback) } shouldBe failure

        lifecycle.isRunning shouldBe false
        failure.suppressed.toList() shouldBe if (sammeException) emptyList() else listOf(processorFailure)
        verifyOrder {
            client.close()
            processor.close()
            callback.run()
        }
    }

    @Test
    fun `Spring lukker komponenter som aldri ble startet`() {
        context(autoStartup = false).use {
            lifecycle.isRunning shouldBe false
        }

        verify(exactly = 0) { client.start() }
        verify(exactly = 0) { processor.start() }
        verify(exactly = 1) { client.close() }
        verify(exactly = 1) { processor.close() }
    }

    @Test
    fun `Spring lukker Kafka i stoppfasen mens databasen er tilgjengelig`() {
        context(autoStartup = true).use {
            lifecycle.isRunning shouldBe true
        }

        verifyOrder {
            client.start()
            processor.start()
            client.close()
            processor.close()
            database.close()
        }
        lifecycle.isRunning shouldBe false
    }

    private fun context(autoStartup: Boolean): AnnotationConfigApplicationContext {
        every { lifecycle.isAutoStartup } returns autoStartup
        val context = AnnotationConfigApplicationContext()
        fun registerResource(name: String, resource: AutoCloseable) {
            context.registerBeanDefinition(
                name,
                RootBeanDefinition(resource.javaClass).apply {
                    instanceSupplier = Supplier { resource }
                    destroyMethodName = "close"
                },
            )
        }
        registerResource("database", database)
        registerResource("kafkaConsumerClient", client)
        registerResource("kafkaConsumerRecordProcessor", processor)
        context.registerBean("kafkaLifecycle", KafkaConsumerLifecycle::class.java, Supplier { lifecycle })
        context.refresh()
        return context
    }
}
