package no.nav.enhetsregister.service

import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.enhetsregister.kafka.KafkaTopicProperties
import no.nav.enhetsregister.kafka.VirksomhetV1Dto
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class KafkaProducerService(
    private val kafkaProducerClient: KafkaProducerClient<ByteArray, ByteArray>,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val objectMapper: ObjectMapper,
) {
    fun publiserVirksomhet(virksomhetDto: VirksomhetV1Dto) {
        val key = virksomhetDto.organisasjonsnummer.toByteArray()
        val value = objectMapper.writeValueAsBytes(virksomhetDto)
        val record = ProducerRecord(kafkaTopicProperties.virksomheterTopic, key, value)
        kafkaProducerClient.sendSync(record)
    }
}
