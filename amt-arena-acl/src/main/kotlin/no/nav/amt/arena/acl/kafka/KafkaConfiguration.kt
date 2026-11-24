package no.nav.amt.arena.acl.kafka

import no.nav.amt.arena.acl.services.ArenaConsumerServiceTemp
import no.nav.amt.arena.acl.services.ArenaMessageConsumerService
import no.nav.common.kafka.consumer.KafkaConsumerClient
import no.nav.common.kafka.consumer.util.KafkaConsumerClientBuilder
import no.nav.common.kafka.consumer.util.deserializer.Deserializers.stringDeserializer
import no.nav.common.kafka.producer.KafkaProducerClientImpl
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.Properties

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaTopicProperties::class)
class KafkaConfiguration(
    @Value($$"${app.env.consumerId}") private val consumerId: String,
    private val tempConsumerId: String = "amt-arena-acl-temp-consumer-v1",
    @Value($$"${app.env.producerId}") private val producerId: String,
) {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun arenaKafkaConsumerClient(
        kafkaTopicProperties: KafkaTopicProperties,
        kafkaProperties: KafkaProperties,
        arenaMessageConsumerService: ArenaMessageConsumerService,
    ): KafkaConsumerClient {
        val topics = listOf(
            kafkaTopicProperties.arenaTiltakGjennomforingTopic,
            kafkaTopicProperties.arenaTiltakDeltakerTopic,
            kafkaTopicProperties.arenaHistTiltakDeltakerTopic,
        )

        val topicConfigs = topics.map { topic ->
            KafkaConsumerClientBuilder
                .TopicConfig<String, String>()
                .withLogging()
                .withConsumerConfig(
                    topic,
                    stringDeserializer(),
                    stringDeserializer(),
                    arenaMessageConsumerService::handleArenaGoldenGateRecord,
                )
        }

        return KafkaConsumerClientBuilder
            .builder()
            .withProperties(kafkaProperties.consumer())
            .withTopicConfigs(topicConfigs)
            .withShutdownHookEnabled(false)
            .build()
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun arenaTempKafkaConsumerClient(
        kafkaTopicProperties: KafkaTopicProperties,
        kafkaProperties: KafkaProperties,
        arenaConsumerServiceTemp: ArenaConsumerServiceTemp,
    ): KafkaConsumerClient {
        val topicConfig = KafkaConsumerClientBuilder
            .TopicConfig<String, String>()
            .withLogging()
            .withConsumerConfig(
                kafkaTopicProperties.arenaTiltakDeltakerTopic,
                stringDeserializer(),
                stringDeserializer(),
                arenaConsumerServiceTemp::handleArenaGoldenGateRecord,
            )

        return KafkaConsumerClientBuilder
            .builder()
            .withProperties(kafkaProperties.tempConsumer())
            .withTopicConfigs(listOf(topicConfig))
            .withShutdownHookEnabled(false)
            .build()
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun kafkaProducer(kafkaProperties: KafkaProperties): KafkaProducerClientImpl<String, String> =
        KafkaProducerClientImpl(kafkaProperties.producer(), false)

    @Bean
    @Profile("default")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun kafkaProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesPreset.aivenDefaultConsumerProperties(consumerId)

        override fun tempConsumer(): Properties = KafkaPropertiesPreset.aivenDefaultConsumerProperties(tempConsumerId)

        override fun producer(): Properties = KafkaPropertiesPreset.aivenDefaultProducerProperties(producerId)
    }

    @Bean
    @Profile("local")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun localKafkaProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesBuilder
            .consumerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withConsumerGroupId(consumerId)
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()

        override fun tempConsumer(): Properties = KafkaPropertiesBuilder
            .consumerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withConsumerGroupId(tempConsumerId)
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()

        override fun producer(): Properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl(("localhost:9092"))
            .withBaseProperties()
            .withProducerId(producerId)
            .withSerializers(StringSerializer::class.java, StringSerializer::class.java)
            .build()
    }
}
