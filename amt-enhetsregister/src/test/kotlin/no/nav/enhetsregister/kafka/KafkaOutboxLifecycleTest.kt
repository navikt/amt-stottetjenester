package no.nav.enhetsregister.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class KafkaOutboxLifecycleTest {
    @Test
    fun `lifecycle starts and stops once and waits for publisher closure`() {
        val processor = mockk<KafkaProducerRecordProcessor>(relaxUnitFun = true)
        val publisher = mockk<KafkaOutboxPublisher>(relaxUnitFun = true)
        val lifecycle = KafkaOutboxLifecycle(processor, publisher)

        lifecycle.stop()
        lifecycle.start()
        lifecycle.start()
        assertThat(lifecycle.isRunning).isTrue()
        lifecycle.stop()
        lifecycle.stop()

        assertThat(lifecycle.isRunning).isFalse()
        verify(exactly = 1) { processor.start() }
        verify(exactly = 1) { publisher.awaitStarted() }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { publisher.awaitClosed() }
        verifyOrder {
            processor.close()
            publisher.awaitClosed()
        }
    }

    @Test
    fun `publisher signals closure after the producer closes`() {
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        val publisher = KafkaOutboxPublisher(producer)
        publisher.close()
        publisher.awaitClosed()
        verify(exactly = 1) { producer.close() }
    }

    @Test
    fun `real processor can stop immediately after startup without racing its worker`() {
        val repository = mockk<PostgresJdbcTemplateProducerRepository>()
        every { repository.getRecords(100, listOf("test-topic")) } returns emptyList()
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        val publisher = KafkaOutboxPublisher(producer)
        val processor = KafkaOutboxProcessorConfig().kafkaProducerRecordProcessor(
            repository,
            publisher,
            LeaderElectionClient { true },
            KafkaTopicProperties("test-topic"),
        )
        val lifecycle = KafkaOutboxLifecycle(processor, publisher)
        try {
            lifecycle.start()
            lifecycle.stop()
            assertThat(lifecycle.isRunning).isFalse()
            verify(exactly = 1) { producer.close() }
        } finally {
            processor.close()
        }
    }

    @Test
    fun `producer closure failure is visible to shutdown waiter`() {
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>()
        every { producer.close() } throws IllegalStateException("Close failed")
        val publisher = KafkaOutboxPublisher(producer)
        assertThatThrownBy { publisher.close() }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { publisher.awaitClosed() }.hasCauseInstanceOf(IllegalStateException::class.java)
    }
}
