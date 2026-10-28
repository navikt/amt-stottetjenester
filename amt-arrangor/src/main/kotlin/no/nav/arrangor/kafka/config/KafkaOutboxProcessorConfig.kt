package no.nav.arrangor.kafka.config

import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import no.nav.arrangor.MetricsService
import no.nav.arrangor.kafka.ANSATT_TOPIC
import no.nav.arrangor.kafka.ARRANGOR_TOPIC
import no.nav.arrangor.kafka.MetricsKafkaProducerRecordPublisher
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.job.leader_election.ShedLockLeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.producer.util.KafkaProducerClientBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProcessorConfig {
    @Bean
    @Profile("!test")
    fun kafkaProducerLockProvider(jdbcTemplate: JdbcTemplate): LockProvider = JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration
            .builder()
            .withJdbcTemplate(jdbcTemplate)
            .usingDbTime()
            .build(),
    )

    @Bean(destroyMethod = "close")
    @Profile("!test")
    fun kafkaProducerLeaderElectionClient(lockProvider: LockProvider) = ShedLockLeaderElectionClient(lockProvider)

    @Bean
    @Profile("!test & !local")
    fun kafkaOutboxProducer(meterRegistry: MeterRegistry): KafkaProducerClient<ByteArray, ByteArray> = KafkaProducerClientBuilder
        .builder<ByteArray, ByteArray>()
        .withProperties(KafkaPropertiesPreset.aivenByteProducerProperties("amt-arrangor-ansatt-outbox"))
        .withMetrics(meterRegistry)
        .build()

    @Bean("kafkaOutboxProducer")
    @Profile("local")
    fun localKafkaOutboxProducer(
        @Value($$"${KAFKA_BROKERS}")
        kafkaBrokers: String,
        meterRegistry: MeterRegistry,
    ): KafkaProducerClient<ByteArray, ByteArray> {
        val properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl(kafkaBrokers)
            .withBaseProperties()
            .withProducerId("amt-arrangor-ansatt-outbox")
            .withSerializers(ByteArraySerializer::class.java, ByteArraySerializer::class.java)
            .build()

        return KafkaProducerClientBuilder
            .builder<ByteArray, ByteArray>()
            .withProperties(properties)
            .withMetrics(meterRegistry)
            .build()
    }

    @Bean
    fun kafkaProducerRecordProcessor(
        producerRepository: PostgresJdbcTemplateProducerRepository,
        @Qualifier("kafkaOutboxProducer")
        kafkaOutboxProducer: KafkaProducerClient<ByteArray, ByteArray>,
        @Qualifier("kafkaProducerLeaderElectionClient")
        kafkaProducerLeaderElectionClient: LeaderElectionClient,
        metricsService: MetricsService,
    ): KafkaProducerRecordProcessor = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(producerRepository)
        .withRecordPublisher(
            MetricsKafkaProducerRecordPublisher(
                delegate = BatchedKafkaProducerRecordPublisher(kafkaOutboxProducer),
                metricsService = metricsService,
            ),
        ).withLeaderElectionClient(kafkaProducerLeaderElectionClient)
        .withTopicWhitelist(
            listOf(
                ANSATT_TOPIC,
                ARRANGOR_TOPIC,
            ),
        ).withRecordsBatchSize(100)
        .withShutdownHookEnabled(false)
        .build()
}
