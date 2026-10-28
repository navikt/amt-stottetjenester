package no.nav.amt.person.service.kafka.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.amt.person.service.kafka.consumer.AktorV2Consumer
import no.nav.amt.person.service.kafka.consumer.InnsatsgruppeConsumer
import no.nav.amt.person.service.kafka.consumer.LeesahConsumer
import no.nav.amt.person.service.kafka.consumer.OppfolgingsperiodeConsumer
import no.nav.amt.person.service.kafka.consumer.SisteOppfolgingsperiodeConsumer
import no.nav.amt.person.service.kafka.consumer.SkjermetPersonConsumer
import no.nav.amt.person.service.kafka.consumer.TildeltVeilederConsumer
import no.nav.common.kafka.consumer.util.KafkaConsumerClientBuilder
import no.nav.common.kafka.consumer.util.deserializer.Deserializers
import no.nav.common.kafka.spring.PostgresJdbcTemplateConsumerRepository
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.leesah.Personhendelse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.util.function.Consumer

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaTopicConfiguration(
    @Value($$"${kafka.schema.registry.url}")
    private val schemaRegistryUrl: String,
    @Value($$"${kafka.schema.registry.username}")
    private val schemaRegistryUsername: String,
    @Value($$"${kafka.schema.registry.password}")
    private val schemaRegistryPassword: String,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val sisteOppfolgingsperiodeConsumer: SisteOppfolgingsperiodeConsumer,
    private val tildeltVeilederConsumer: TildeltVeilederConsumer,
    private val aktorV2Consumer: AktorV2Consumer,
    private val skjermetPersonConsumer: SkjermetPersonConsumer,
    private val leesahConsumer: LeesahConsumer,
    private val oppfolgingsperiodeConsumer: OppfolgingsperiodeConsumer,
    private val innsatsgruppeConsumer: InnsatsgruppeConsumer,
) {
    @Bean
    fun consumerRepository(jdbcTemplate: JdbcTemplate) = PostgresJdbcTemplateConsumerRepository(jdbcTemplate)

    @Bean
    fun topicConfigs(
        consumerRepository: PostgresJdbcTemplateConsumerRepository,
        meterRegistry: MeterRegistry,
    ) = listOf(
        KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.sisteOppfolgingsperiodeTopic,
                Deserializers.stringDeserializer(),
                Deserializers.stringDeserializer(),
                Consumer { sisteOppfolgingsperiodeConsumer.ingest(it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.sisteTilordnetVeilederTopic,
                Deserializers.stringDeserializer(),
                Deserializers.stringDeserializer(),
                Consumer { tildeltVeilederConsumer.ingest(it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.oppfolgingsperiodeTopic,
                Deserializers.stringDeserializer(),
                Deserializers.stringDeserializer(),
                Consumer { oppfolgingsperiodeConsumer.ingest(it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.innsatsgruppeTopic,
                Deserializers.stringDeserializer(),
                Deserializers.stringDeserializer(),
                Consumer { innsatsgruppeConsumer.ingest(it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, Aktor>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.aktorV2Topic,
                Deserializers.stringDeserializer(),
                SpecificAvroDeserializer(
                    schemaRegistryUrl,
                    schemaRegistryUsername,
                    schemaRegistryPassword,
                ),
                Consumer { aktorV2Consumer.ingest(it.key(), it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, Personhendelse>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.leesahTopic,
                Deserializers.stringDeserializer(),
                SpecificAvroDeserializer(
                    schemaRegistryUrl,
                    schemaRegistryUsername,
                    schemaRegistryPassword,
                ),
                Consumer { leesahConsumer.ingest(it.value()) },
            ),
        KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withMetrics(meterRegistry)
            .withStoreOnFailure(consumerRepository)
            .withConsumerConfig(
                kafkaTopicProperties.skjermedePersonerTopic,
                Deserializers.stringDeserializer(),
                Deserializers.stringDeserializer(),
                Consumer {
                    it
                        .value()
                        ?.let { payload ->
                            skjermetPersonConsumer.ingest(it.key(), payload)
                        }
                        ?: skjermetPersonConsumer.ingestTombstone(it.key())
                },
            ),
    )
}
