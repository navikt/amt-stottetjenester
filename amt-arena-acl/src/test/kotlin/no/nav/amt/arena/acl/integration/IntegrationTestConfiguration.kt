package no.nav.amt.arena.acl.integration

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageConsumer
import no.nav.amt.arena.acl.kafka.KafkaProperties
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties

@Profile("integration")
@EnableJwtTokenValidation
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfiguration {
    @Bean
    @Primary
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true")
    fun kafkaProperties(): KafkaProperties {
        val host = SmokeKafkaBroker.container.bootstrapServers

        return object : KafkaProperties {
            override fun consumer(): Properties = KafkaPropertiesBuilder
                .consumerBuilder()
                .withBrokerUrl(host)
                .withBaseProperties()
                .withConsumerGroupId("INTEGRATION_CONSUMER")
                .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
                .build()

            override fun tempConsumer(): Properties = KafkaPropertiesBuilder
                .consumerBuilder()
                .withBrokerUrl(host)
                .withBaseProperties()
                .withConsumerGroupId("INTEGRATION_TEMP_CONSUMER")
                .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
                .build()

            override fun producer(): Properties = KafkaPropertiesBuilder
                .producerBuilder()
                .withBrokerUrl(host)
                .withBaseProperties()
                .withProducerId("INTEGRATION_PRODUCER")
                .withSerializers(StringSerializer::class.java, StringSerializer::class.java)
                .build()
        }
    }

    @Bean
    @ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "false")
    fun capturedKafkaProducer(records: KafkaMessageConsumer): KafkaProducerClient<String, String> =
        mockk<KafkaProducerClient<String, String>>().also { producer ->
            every { producer.sendSync(any()) } answers {
                val record = firstArg<ProducerRecord<String, String>>()
                records.capture(record)
                RecordMetadata(TopicPartition(record.topic(), record.partition() ?: 0), 0L, 0, 0L, 0, 0)
            }
            every { producer.close() } just Runs
        }

    private object SmokeKafkaBroker {
        val container by lazy {
            KafkaContainer(DockerImageName.parse("apache/kafka")).apply {
                // Omgår https://github.com/testcontainers/testcontainers-java/issues/9506
                withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
                start()
            }
        }
    }
}
