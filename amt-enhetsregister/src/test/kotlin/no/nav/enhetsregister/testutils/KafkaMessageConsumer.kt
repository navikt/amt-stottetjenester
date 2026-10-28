package no.nav.enhetsregister.testutils

import no.nav.enhetsregister.testutils.KafkaTestConfiguration.Companion.kafkaContainer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties

object KafkaMessageConsumer {
    fun consume(topic: String): ConsumerRecords<String, String>? {
        val consumer = KafkaConsumer<String, String>(
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, "CONSUMER_ID")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            },
        )

        consumer.subscribe(listOf(topic))
        val records = consumer.poll(Duration.ofSeconds(5))
        consumer.close()

        return records
    }
}
