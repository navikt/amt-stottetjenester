package no.nav.amt.tiltak.kafka.config

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import no.nav.amt.tiltak.core.kafka.AmtArrangorIngestor
import no.nav.amt.tiltak.core.kafka.AnsattIngestor
import no.nav.amt.tiltak.core.kafka.ArenaAclIngestor
import no.nav.amt.tiltak.core.kafka.GjennomforingIngestor
import no.nav.amt.tiltak.core.kafka.NavAnsattIngestor
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.feilhandtering.KafkaConsumerRecordProcessor
import no.nav.common.kafka.consumer.feilhandtering.util.KafkaConsumerRecordProcessorBuilder
import no.nav.common.kafka.consumer.util.KafkaConsumerClientBuilder
import no.nav.common.kafka.consumer.util.deserializer.Deserializers.stringDeserializer
import no.nav.common.kafka.spring.PostgresJdbcTemplateConsumerRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import java.util.function.Consumer

@Configuration
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaConfiguration(
    kafkaTopicProperties: KafkaTopicProperties,
    kafkaProperties: KafkaProperties,
    jdbcTemplate: JdbcTemplate,
    arenaAclIngestor: ArenaAclIngestor,
    gjennomforingIngestor: GjennomforingIngestor,
    arrangorIngestor: AmtArrangorIngestor,
    ansattIngestor: AnsattIngestor,
    navAnsattIngestor: NavAnsattIngestor,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var client: KafkaConsumerClient
    private var consumerRepository = PostgresJdbcTemplateConsumerRepository(jdbcTemplate)
    private var consumerRecordProcessor: KafkaConsumerRecordProcessor

    init {
        val topicConfigs = mutableListOf<KafkaConsumerClientBuilder.TopicConfig<String, out Any>>()

        topicConfigs.add(
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withStoreOnFailure(consumerRepository)
                .withConsumerConfig(
                    kafkaTopicProperties.amtTiltakTopic,
                    stringDeserializer(),
                    stringDeserializer(),
                    Consumer { arenaAclIngestor.ingestKafkaRecord(it.value()) },
                ),
        )

        topicConfigs.add(
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withStoreOnFailure(consumerRepository)
                .withConsumerConfig(
                    kafkaTopicProperties.sisteTiltaksgjennomforingerTopic,
                    stringDeserializer(),
                    stringDeserializer(),
                    Consumer { gjennomforingIngestor.ingestKafkaRecord(it.key(), it.value()) },
                ),
        )

        topicConfigs.add(
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withStoreOnFailure(consumerRepository)
                .withConsumerConfig(
                    kafkaTopicProperties.amtArrangorTopic,
                    stringDeserializer(),
                    stringDeserializer(),
                    Consumer { arrangorIngestor.ingestArrangor(it.value()) },
                ),
        )

        topicConfigs.add(
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withStoreOnFailure(consumerRepository)
                .withConsumerConfig(
                    kafkaTopicProperties.amtArrangorAnsattTopic,
                    stringDeserializer(),
                    stringDeserializer(),
                    Consumer { ansattIngestor.ingestAnsatt(it.value()) },
                ),
        )
        topicConfigs.add(
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withStoreOnFailure(consumerRepository)
                .withConsumerConfig(
                    kafkaTopicProperties.amtNavAnsattPersonaliaTopic,
                    stringDeserializer(),
                    stringDeserializer(),
                    Consumer { navAnsattIngestor.ingest(it.value()) },
                ),
        )

        consumerRecordProcessor =
            KafkaConsumerRecordProcessorBuilder
                .builder()
                .withRecordBatchSize(2000)
                .withLockProvider(JdbcTemplateLockProvider(jdbcTemplate))
                .withKafkaConsumerRepository(consumerRepository)
                .withConsumerConfigs(topicConfigs.map { it.consumerConfig })
                .build()

        client =
            KafkaConsumerClientBuilder
                .builder()
                .withProperties(kafkaProperties.consumer())
                .withTopicConfigs(topicConfigs.toList())
                .build()
    }

    @EventListener(ContextRefreshedEvent::class)
    fun onApplicationEvent() {
        log.info("Starting kafka consumer and stored record processor...")
        client.start()
        consumerRecordProcessor.start()
    }
}
