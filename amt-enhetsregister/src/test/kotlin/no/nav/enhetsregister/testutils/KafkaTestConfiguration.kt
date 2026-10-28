package no.nav.enhetsregister.testutils

import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.enhetsregister.kafka.KafkaProducerProperties
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties

@TestConfiguration
class KafkaTestConfiguration {
    @Bean
    fun kafkaProducerProperties() = object : KafkaProducerProperties {
        override fun producer(): Properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl(kafkaContainer.bootstrapServers)
            .withBaseProperties()
            .withProducerId(PRODUCER_ID)
            .withSerializers(ByteArraySerializer::class.java, ByteArraySerializer::class.java)
            .build()
    }

    companion object {
        private const val PRODUCER_ID = "INTEGRATION_PRODUCER"

        val kafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka")).apply {
            // workaround for https://github.com/testcontainers/testcontainers-java/issues/9506
            withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
            start()
            System.setProperty("KAFKA_BROKERS", bootstrapServers)
        }
    }
}
