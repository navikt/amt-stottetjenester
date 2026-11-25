package no.nav.amt.tiltak.kafka.config

import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.util.KafkaProducerClientBuilder
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.Properties

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaTopicProperties::class)
class KafkaBeans {
    @Bean
    @Profile("default")
    fun kafkaConsumerProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesPreset.aivenDefaultConsumerProperties("amt-tiltak-consumer.v10")

        override fun producer(): Properties = KafkaPropertiesPreset.aivenByteProducerProperties("amt-tiltak-producer")
    }

    @Bean
    @Profile("local")
    fun localKafkaConsumerProperties(): KafkaProperties = object : KafkaProperties {
        override fun consumer(): Properties = KafkaPropertiesBuilder
            .consumerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withConsumerGroupId("amt-tiltak-consumer.v1")
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()

        override fun producer(): Properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withProducerId("amt-tiltak-producer")
            .withSerializers(ByteArraySerializer::class.java, ByteArraySerializer::class.java)
            .build()
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    fun kafkaProducer(kafkaProperties: KafkaProperties): KafkaProducerClient<ByteArray, ByteArray> = KafkaProducerClientBuilder
        .builder<ByteArray, ByteArray>()
        .withProperties(kafkaProperties.producer())
        .withShutdownHookEnabled(false)
        .build()

    @Bean(name = ["stringKafkaProducer"], destroyMethod = "close")
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
    @Profile("default")
    fun stringKafkaProducer(): KafkaProducerClient<String, String> {
        val properties = KafkaPropertiesPreset.aivenDefaultProducerProperties("amt-tiltak-producer-2")
        return KafkaProducerClientBuilder
            .builder<String, String>()
            .withProperties(properties)
            .withShutdownHookEnabled(false)
            .build()
    }
}
