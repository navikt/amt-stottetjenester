package no.nav.arrangor.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaConfig(
    @Value($$"${KAFKA_BROKERS}") private val kafkaBrokers: String,
    @Value($$"${KAFKA_SECURITY_PROTOCOL:SSL}") private val kafkaSecurityProtocol: String,
    @Value($$"${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
    @Value($$"${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
    @Value($$"${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
    @Value($$"${kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
) {
    private val javaKeystore = "JKS"
    private val pkcs12 = "PKCS12"

    private val securityConfig = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
        // Disable server host name verification
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to javaKeystore,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to pkcs12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
    )

    val commonKafkaConfig = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
    ) + securityConfig

    @Bean
    fun kafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler) =
        createKafkaListenerContainerFactory("amt-arrangor-consumer-2", kafkaErrorHandler)

    @Bean
    fun kafkaListenerContainerFactoryDeltakerTopic(kafkaErrorHandler: KafkaErrorHandler) =
        createKafkaListenerContainerFactory("amt-arrangor-consumer-4", kafkaErrorHandler)

    private fun createKafkaListenerContainerFactory(
        groupId: String,
        kafkaErrorHandler: KafkaErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val config = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        ) + commonKafkaConfig

        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            @Suppress("UsePropertyAccessSyntax")
            setConsumerFactory(DefaultKafkaConsumerFactory(config))
            setCommonErrorHandler(kafkaErrorHandler)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        }
    }
}
