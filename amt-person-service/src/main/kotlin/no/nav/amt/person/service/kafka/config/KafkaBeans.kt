package no.nav.amt.person.service.kafka.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.util.KafkaProducerClientBuilder
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.Properties

@Configuration(proxyBeanMethods = false)
class KafkaBeans {
    @Bean
    @Profile("default")
    fun kafkaConsumerProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesPreset.aivenDefaultConsumerProperties(CONSUMER_GROUP_ID)

        override fun producer(): Properties = KafkaPropertiesPreset.aivenDefaultProducerProperties(PRODUCER_ID)
    }

    @Bean
    @Profile("local")
    fun kafkaLocalProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesBuilder
            .consumerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withConsumerGroupId("amt-person-service-local-consumer")
            .withDeserializers(StringDeserializer::class.java, StringDeserializer::class.java)
            .build()

        override fun producer(): Properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withProducerId("amt-person-service-local-producer")
            .withSerializers(StringSerializer::class.java, StringSerializer::class.java)
            .build()
    }

    @Bean
    @ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
    fun kafkaOutboxProducer(
        kafkaProperties: KafkaProperties,
        meterRegistry: MeterRegistry,
    ): KafkaProducerClient<ByteArray, ByteArray> {
        val properties = Properties().apply {
            putAll(kafkaProperties.producer())
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
        }

        return KafkaProducerClientBuilder
            .builder<ByteArray, ByteArray>()
            .withProperties(properties)
            .withMetrics(meterRegistry)
            .build()
    }

    companion object {
        private const val CONSUMER_GROUP_ID = "amt-person-service-consumer.v1"
        private const val PRODUCER_ID = "amt-person-service-producer"
    }
}
