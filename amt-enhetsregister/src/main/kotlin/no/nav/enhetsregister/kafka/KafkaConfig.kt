package no.nav.enhetsregister.kafka

import no.nav.common.kafka.producer.KafkaProducerClientImpl
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.Properties

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaTopicProperties::class)
class KafkaConfig {
    @Bean
    @Profile("default")
    fun kafkaProducerProperties(): KafkaProducerProperties = object : KafkaProducerProperties {
        override fun producer(): Properties = KafkaPropertiesPreset.aivenByteProducerProperties("amt-enhetsregister")
    }

    @Bean
    @Profile("local")
    fun kafkaLocalProducerProperties(): KafkaProducerProperties = object : KafkaProducerProperties {
        override fun producer(): Properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl("localhost:9092")
            .withBaseProperties()
            .withProducerId("amt-enhetsregister-local-producer")
            .withSerializers(ByteArraySerializer::class.java, ByteArraySerializer::class.java)
            .build()
    }

    @Bean
    fun kafkaProducer(kafkaProducerProperties: KafkaProducerProperties) =
        KafkaProducerClientImpl<ByteArray, ByteArray>(kafkaProducerProperties.producer())
}
