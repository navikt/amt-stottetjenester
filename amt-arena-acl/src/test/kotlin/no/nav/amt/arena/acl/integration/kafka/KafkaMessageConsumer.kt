package no.nav.amt.arena.acl.integration.kafka

import no.nav.amt.arena.acl.kafka.KafkaTopicProperties
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "false")
class KafkaMessageConsumer(
    private val kafkaTopicProperties: KafkaTopicProperties,
) {
    private val records = CopyOnWriteArrayList<ProducerRecord<String, String>>()

    fun capture(record: ProducerRecord<String, String>) {
        check(record.topic() in setOf(kafkaTopicProperties.amtTopic, kafkaTopicProperties.amtEnkeltplassDeltakerTopic))
        check(!record.key().isNullOrBlank())
        records.add(record)
    }

    fun getRecords(topic: Topic): List<ProducerRecord<String, String>> = records.filter { it.topic() == mapKafkaTopic(topic) }

    fun getLatestRecord(topic: Topic): ProducerRecord<String, String>? = getRecords(topic).lastOrNull()

    fun reset() = records.clear()

    private fun mapKafkaTopic(topic: Topic): String = when (topic) {
        Topic.AMT_TILTAK -> kafkaTopicProperties.amtTopic
        Topic.AMT_ENKELTPLASS_DELTAKER -> kafkaTopicProperties.amtEnkeltplassDeltakerTopic
    }

    enum class Topic {
        AMT_TILTAK,
        AMT_ENKELTPLASS_DELTAKER,
    }
}
