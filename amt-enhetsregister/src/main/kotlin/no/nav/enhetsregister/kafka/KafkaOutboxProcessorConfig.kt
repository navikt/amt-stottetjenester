package no.nav.enhetsregister.kafka

import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxLifecycle
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxRuntime
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProcessorConfig {
    @Bean
    fun kafkaOutboxRuntime(
        producerRepository: PostgresJdbcTemplateProducerRepository,
        kafkaProducer: KafkaProducerClient<ByteArray, ByteArray>,
        leaderElectionClient: LeaderElectionClient,
        kafkaTopicProperties: KafkaTopicProperties,
    ) = KafkaOutboxRuntime.create(
        producerRepository = producerRepository,
        producerClient = kafkaProducer,
        leaderElectionClient = leaderElectionClient,
        topicWhitelist = listOf(kafkaTopicProperties.virksomheterTopic),
        recordsBatchSize = 100,
    )

    @Bean
    fun kafkaOutboxLifecycle(runtime: KafkaOutboxRuntime) = KafkaOutboxLifecycle(runtime)
}
