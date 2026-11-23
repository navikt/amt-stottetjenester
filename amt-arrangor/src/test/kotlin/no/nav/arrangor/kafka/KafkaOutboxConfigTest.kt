package no.nav.arrangor.kafka

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxLifecycle
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxRuntime
import no.nav.arrangor.MetricsService
import no.nav.arrangor.kafka.config.KafkaOutboxProcessorConfig
import no.nav.arrangor.kafka.config.KafkaOutboxStorageConfig
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class KafkaOutboxConfigTest {
    @Test
    fun `deaktivert Kafka beholder lagring og fersk backlog-måling uten producer eller livssyklus`() {
        val jdbcTemplate = mockk<JdbcTemplate>(relaxUnitFun = true)
        every { jdbcTemplate.queryForObject("SELECT count(*) FROM kafka_producer_record", Long::class.java) } returnsMany listOf(4L, 2L)
        val registry = SimpleMeterRegistry()
        try {
            ApplicationContextRunner()
                .withUserConfiguration(
                    KafkaOutboxStorageConfig::class.java,
                    KafkaOutboxProcessorConfig::class.java,
                    MetricsService::class.java,
                ).withBean(JdbcTemplate::class.java, Supplier { jdbcTemplate })
                .withBean(MeterRegistry::class.java, Supplier { registry })
                .withPropertyValues("kafka.enabled=false")
                .run { context ->
                    assertThat(context).hasNotFailed()
                    assertThat(context).hasSingleBean(KafkaProducerRecordStorage::class.java)
                    assertThat(context).hasSingleBean(PostgresJdbcTemplateProducerRepository::class.java)
                    assertThat(context).doesNotHaveBean(KafkaProducerClient::class.java)
                    assertThat(context).doesNotHaveBean(KafkaOutboxRuntime::class.java)
                    assertThat(context).doesNotHaveBean(KafkaOutboxLifecycle::class.java)
                    val gauge = registry.get("amt_arrangor_kafka_outbox_ventende").gauge()
                    gauge.value() shouldBe 4.0
                    gauge.value() shouldBe 2.0
                }
        } finally {
            registry.close()
        }
    }

    @Test
    fun `runtime beholder begge topics og teller bare bekreftede publiseringer`() {
        val repository = mockk<PostgresJdbcTemplateProducerRepository>()
        val records = listOf(
            StoredProducerRecord(1, ANSATT_TOPIC, byteArrayOf(1), byteArrayOf(2), "[]"),
            StoredProducerRecord(2, ARRANGOR_TOPIC, byteArrayOf(3), byteArrayOf(4), "[]"),
        )
        every { repository.getRecords(100, listOf(ANSATT_TOPIC, ARRANGOR_TOPIC)) } returnsMany listOf(records, emptyList())
        val deleted = CountDownLatch(1)
        every { repository.deleteRecords(listOf(1L)) } answers { deleted.countDown() }
        val producer = mockk<Producer<ByteArray, ByteArray>>(relaxUnitFun = true)
        val client = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        every { client.producer } returns producer
        every { client.send(any(), any()) } answers {
            val record = firstArg<ProducerRecord<ByteArray, ByteArray>>()
            secondArg<Callback>().onCompletion(null, if (record.topic() == ANSATT_TOPIC) null else IllegalStateException("Send failed"))
            mockk()
        }
        val registry = SimpleMeterRegistry()
        try {
            val metrics = MetricsService(registry, mockk())
            val configuration = KafkaOutboxProcessorConfig()
            configuration.kafkaOutboxRuntime(repository, client, LeaderElectionClient { true }, metrics).use { runtime ->
                val lifecycle = configuration.kafkaOutboxLifecycle(runtime)
                lifecycle.start()
                deleted.await(2, TimeUnit.SECONDS) shouldBe true
                registry.get("amt_arrangor_publiserte_ansatte").counter().count() shouldBe 1.0
                registry.get("amt_arrangor_publiserte_arrangorer").counter().count() shouldBe 0.0
                lifecycle.stop()
            }
        } finally {
            registry.close()
        }

        verify(exactly = 1) { client.close() }
        verify(exactly = 1) { repository.deleteRecords(listOf(1L)) }
    }

    @Test
    fun `runtime eier producer-lukking i både lokalt og ordinært profiloppsett`() {
        listOf("kafkaOutboxProducer", "localKafkaOutboxProducer").forEach { name ->
            val bean = KafkaOutboxProcessorConfig::class.java.methods
                .single { it.name == name }
                .getAnnotation(Bean::class.java)
            bean.destroyMethod shouldBe ""
        }
    }
}
