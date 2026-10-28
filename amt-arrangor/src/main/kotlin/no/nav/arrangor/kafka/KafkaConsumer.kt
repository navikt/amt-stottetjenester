package no.nav.arrangor.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaConsumer(
    private val consumerService: ConsumerService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [VIRKSOMHET_TOPIC, ANSATT_PERSONALIA_TOPIC],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun listener(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        when (record.topic()) {
            VIRKSOMHET_TOPIC -> consumerService.handleVirksomhetEndring(record.value()?.let { objectMapper.readValue(it) })
            ANSATT_PERSONALIA_TOPIC -> consumerService.handleAnsattPersonalia(objectMapper.readValue(record.value()))
            else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
        }
        ack.acknowledge()
    }

    @KafkaListener(
        topics = [DELTAKER_TOPIC],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "kafkaListenerContainerFactoryDeltakerTopic",
    )
    fun deltakerListener(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        when (record.topic()) {
            DELTAKER_TOPIC ->
                consumerService.handleDeltakerEndring(
                    id = UUID.fromString(record.key()),
                    deltaker = record.value()?.let { objectMapper.readValue(it) },
                )

            else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
        }
        ack.acknowledge()
    }
}
