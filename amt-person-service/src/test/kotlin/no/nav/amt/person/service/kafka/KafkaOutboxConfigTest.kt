package no.nav.amt.person.service.kafka

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxLifecycle
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxRuntime
import no.nav.amt.person.service.kafka.config.KafkaBeans
import no.nav.amt.person.service.kafka.config.KafkaConfiguration
import no.nav.amt.person.service.kafka.config.KafkaOutboxStorageConfiguration
import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import no.nav.amt.person.service.metrics.MetricRepository
import no.nav.amt.person.service.metrics.MetricService
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
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
    fun `deaktivert Kafka beholder lagring og fersk backlog-måling uten Kafka-livssyklus`() {
        val jdbcTemplate = mockk<JdbcTemplate>(relaxUnitFun = true)
        every { jdbcTemplate.queryForObject("SELECT count(*) FROM kafka_producer_record", Long::class.java) } returnsMany listOf(7L, 1L)
        val registry = SimpleMeterRegistry()
        try {
            ApplicationContextRunner()
                .withUserConfiguration(
                    KafkaBeans::class.java,
                    KafkaConfiguration::class.java,
                    KafkaOutboxStorageConfiguration::class.java,
                    KafkaConsumerLifecycle::class.java,
                    MetricService::class.java,
                ).withBean(JdbcTemplate::class.java, Supplier { jdbcTemplate })
                .withBean(MeterRegistry::class.java, Supplier { registry })
                .withBean(MetricRepository::class.java, Supplier { mockk<MetricRepository>() })
                .withPropertyValues("kafka.enabled=false")
                .run { context ->
                    assertThat(context).hasNotFailed()
                    assertThat(context).hasSingleBean(KafkaProducerRecordStorage::class.java)
                    assertThat(context).hasSingleBean(PostgresJdbcTemplateProducerRepository::class.java)
                    assertThat(context).doesNotHaveBean(KafkaProducerClient::class.java)
                    assertThat(context).doesNotHaveBean(KafkaConsumerClient::class.java)
                    assertThat(context).doesNotHaveBean(KafkaConsumerRecordProcessor::class.java)
                    assertThat(context).doesNotHaveBean(KafkaConsumerLifecycle::class.java)
                    assertThat(context).doesNotHaveBean(KafkaOutboxRuntime::class.java)
                    assertThat(context).doesNotHaveBean(KafkaOutboxLifecycle::class.java)
                    val gauge = registry.get("amt_person_kafka_outbox_ventende").gauge()
                    gauge.value() shouldBe 7.0
                    gauge.value() shouldBe 1.0
                }
        } finally {
            registry.close()
        }
    }

    @Test
    fun `runtime bruker fire producer-topics og lukker før consumere stoppes`() {
        val topics = mockk<KafkaTopicProperties>()
        every { topics.amtNavBrukerTopic } returns "nav-bruker"
        every { topics.amtArrangorAnsattPersonaliaTopic } returns "arrangor-ansatt"
        every { topics.amtNavAnsattPersonaliaTopic } returns "nav-ansatt"
        every { topics.amtNavEnhetTopic } returns "nav-enhet"
        val whitelist = listOf("nav-bruker", "arrangor-ansatt", "nav-ansatt", "nav-enhet")
        val polled = CountDownLatch(1)
        val repository = mockk<PostgresJdbcTemplateProducerRepository>()
        every { repository.getRecords(100, whitelist) } answers {
            polled.countDown()
            emptyList()
        }
        val producer = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        val consumer = mockk<KafkaConsumerClient>(relaxUnitFun = true)
        val consumerProcessor = mockk<KafkaConsumerRecordProcessor>(relaxUnitFun = true)
        KafkaConfiguration().kafkaOutboxRuntime(repository, producer, LeaderElectionClient { true }, topics).use { runtime ->
            val lifecycle = KafkaConsumerLifecycle(consumer, consumerProcessor, runtime)
            lifecycle.start()
            try {
                polled.await(2, TimeUnit.SECONDS) shouldBe true
            } finally {
                lifecycle.stop()
            }
        }

        verifyOrder {
            consumer.start()
            consumerProcessor.start()
            repository.getRecords(100, whitelist)
            producer.close()
            consumerProcessor.stop()
            consumer.stop()
        }
        verify(exactly = 1) { producer.close() }
    }

    @Test
    fun `runtime eier producer-lukking og appen beholder samordnet livssyklus`() {
        val producerBean = KafkaBeans::class.java.methods
            .single { it.name == "kafkaOutboxProducer" }
            .getAnnotation(Bean::class.java)
        producerBean.destroyMethod shouldBe ""
        KafkaConfiguration::class.java.methods.none { it.returnType == KafkaOutboxLifecycle::class.java } shouldBe true
    }
}
