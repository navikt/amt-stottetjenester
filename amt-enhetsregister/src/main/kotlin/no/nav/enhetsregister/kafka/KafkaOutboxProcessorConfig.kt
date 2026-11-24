package no.nav.enhetsregister.kafka

import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProcessorConfig {
    @Bean(destroyMethod = "")
    fun kafkaOutboxPublisher(kafkaProducer: KafkaProducerClient<ByteArray, ByteArray>) = KafkaOutboxPublisher(kafkaProducer)

    @Bean(destroyMethod = "")
    fun kafkaProducerRecordProcessor(
        producerRepository: PostgresJdbcTemplateProducerRepository,
        publisher: KafkaOutboxPublisher,
        leaderElectionClient: LeaderElectionClient,
        kafkaTopicProperties: KafkaTopicProperties,
    ) = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(producerRepository)
        .withRecordPublisher(publisher)
        .withLeaderElectionClient {
            publisher.processorStarted()
            leaderElectionClient.isLeader
        }
        .withTopicWhitelist(listOf(kafkaTopicProperties.virksomheterTopic))
        .withRecordsBatchSize(100)
        .withShutdownHookEnabled(false)
        .build()
}
