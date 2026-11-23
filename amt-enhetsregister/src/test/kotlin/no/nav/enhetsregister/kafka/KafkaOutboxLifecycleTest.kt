package no.nav.enhetsregister.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaOutboxLifecycleTest {
    @Test
    fun `delt livssyklus bruker appens topic og lukker producer én gang`() {
        val polled = CountDownLatch(1)
        val repository = mockk<PostgresJdbcTemplateProducerRepository>()
        every { repository.getRecords(100, listOf("test-topic")) } answers {
            polled.countDown()
            emptyList()
        }
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        val configuration = KafkaOutboxProcessorConfig()
        configuration.kafkaOutboxRuntime(
            repository,
            producer,
            LeaderElectionClient { true },
            KafkaTopicProperties("test-topic"),
        ).use { runtime ->
            val lifecycle = configuration.kafkaOutboxLifecycle(runtime)
            lifecycle.stop()
            lifecycle.start()
            lifecycle.start()
            assertThat(lifecycle.isRunning).isTrue()
            assertThat(polled.await(2, TimeUnit.SECONDS)).isTrue()
            lifecycle.stop()
            lifecycle.stop()
            assertThat(lifecycle.isRunning).isFalse()
        }

        verify(exactly = 1) { producer.close() }
    }

    @Test
    fun `feil ved producer-lukking når fram til den delte livssyklusen`() {
        val repository = mockk<PostgresJdbcTemplateProducerRepository>()
        every { repository.getRecords(any(), any()) } returns emptyList()
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>()
        every { producer.close() } throws IllegalStateException("Close failed")
        val configuration = KafkaOutboxProcessorConfig()
        val runtime = configuration.kafkaOutboxRuntime(
            repository,
            producer,
            LeaderElectionClient { true },
            KafkaTopicProperties("test-topic"),
        )
        val lifecycle = configuration.kafkaOutboxLifecycle(runtime)
        lifecycle.start()

        assertThatThrownBy { lifecycle.stop() }.hasCauseInstanceOf(IllegalStateException::class.java)
        verify(exactly = 1) { producer.close() }
    }

    @Test
    fun `Spring lukker runtime men ikke producer separat`() {
        val producerBean = KafkaConfig::class.java.methods.single { it.name == "kafkaProducer" }.getAnnotation(Bean::class.java)
        val runtimeBean = KafkaOutboxProcessorConfig::class.java.methods.single { it.name == "kafkaOutboxRuntime" }.getAnnotation(Bean::class.java)

        assertThat(producerBean.destroyMethod).isEmpty()
        assertThat(runtimeBean.destroyMethod).isEqualTo("(inferred)")
    }
}
