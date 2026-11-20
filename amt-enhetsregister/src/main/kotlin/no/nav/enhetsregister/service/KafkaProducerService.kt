package no.nav.enhetsregister.service

import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.enhetsregister.kafka.KafkaTopicProperties
import no.nav.enhetsregister.kafka.VirksomhetV1Dto
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Service
@Transactional(propagation = Propagation.MANDATORY)
class KafkaProducerService(
    private val producerRecordStorage: KafkaProducerRecordStorage,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val objectMapper: ObjectMapper,
) {
    fun publiserVirksomhet(virksomhetDto: VirksomhetV1Dto) {
        val key = virksomhetDto.organisasjonsnummer.toByteArray()
        val value = objectMapper.writeValueAsBytes(virksomhetDto)
        val record = ProducerRecord(kafkaTopicProperties.virksomheterTopic, key, value)
        producerRecordStorage.store(record)
    }
}
