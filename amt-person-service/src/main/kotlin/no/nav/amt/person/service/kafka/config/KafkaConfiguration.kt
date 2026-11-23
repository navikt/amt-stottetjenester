package no.nav.amt.person.service.kafka.config

import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxRuntime
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.job.leader_election.ShedLockLeaderElectionClient
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import no.nav.common.kafka.consumer.feilhandtering.util.KafkaConsumerRecordProcessorBuilder
import no.nav.common.kafka.consumer.util.KafkaConsumerClientBuilder
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.spring.PostgresJdbcTemplateConsumerRepository
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxStorageConfiguration as SharedKafkaOutboxStorageConfiguration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaConfiguration {
    @Bean
    fun kafkaConsumerClient(
        kafkaProperties: KafkaProperties,
        topicConfigs: List<KafkaConsumerClientBuilder.TopicConfig<*, *>>,
    ): KafkaConsumerClient = KafkaConsumerClientBuilder
        .builder()
        .withProperties(kafkaProperties.consumer())
        .withTopicConfigs(topicConfigs)
        .build()

    @Bean
    fun kafkaConsumerRecordProcessor(
        topicConfigs: List<KafkaConsumerClientBuilder.TopicConfig<*, *>>,
        consumerRepository: PostgresJdbcTemplateConsumerRepository,
        jdbcTemplate: JdbcTemplate,
        meterRegistry: MeterRegistry,
    ): KafkaConsumerRecordProcessor = KafkaConsumerRecordProcessorBuilder
        .builder()
        .withLockProvider(JdbcTemplateLockProvider(jdbcTemplate))
        .withKafkaConsumerRepository(consumerRepository)
        .withTopicConfigs(topicConfigs)
        .withMetrics(meterRegistry)
        .build()

    @Bean(destroyMethod = "close")
    fun kafkaProducerLeaderElectionClient(lockProvider: LockProvider) = ShedLockLeaderElectionClient(lockProvider)

    @Bean
    fun kafkaOutboxRuntime(
        producerRepository: PostgresJdbcTemplateProducerRepository,
        @Qualifier("kafkaOutboxProducer")
        kafkaOutboxProducer: KafkaProducerClient<ByteArray, ByteArray>,
        @Qualifier("kafkaProducerLeaderElectionClient")
        kafkaProducerLeaderElectionClient: LeaderElectionClient,
        kafkaTopicProperties: KafkaTopicProperties,
    ): KafkaOutboxRuntime = KafkaOutboxRuntime.create(
        producerRepository = producerRepository,
        producerClient = kafkaOutboxProducer,
        leaderElectionClient = kafkaProducerLeaderElectionClient,
        topicWhitelist = listOf(
            kafkaTopicProperties.amtNavBrukerTopic,
            kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic,
            kafkaTopicProperties.amtNavAnsattPersonaliaTopic,
            kafkaTopicProperties.amtNavEnhetTopic,
        ),
        recordsBatchSize = 100,
    )
}

@Configuration(proxyBeanMethods = false)
@Import(SharedKafkaOutboxStorageConfiguration::class)
class KafkaOutboxStorageConfiguration
