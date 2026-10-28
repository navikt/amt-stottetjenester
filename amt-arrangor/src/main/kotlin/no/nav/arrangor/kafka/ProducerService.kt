package no.nav.arrangor.kafka

import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.dto.AMT_ARRANGOR_SOURCE
import no.nav.arrangor.dto.AnsattDto
import no.nav.arrangor.dto.ArrangorDto
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.util.ProducerUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
@Transactional(propagation = Propagation.MANDATORY)
class ProducerService(
    private val producerRecordStorage: KafkaProducerRecordStorage,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishArrangor(arrangor: Arrangor) {
        val record = ProducerRecord(
            ARRANGOR_TOPIC,
            arrangor.id.toString(),
            objectMapper.writeValueAsString(arrangor.toDto()),
        )
        producerRecordStorage.store(ProducerUtils.serializeStringRecord(record))
        logger.info("La arrangørmelding i Kafka-outbox for arrangør ${arrangor.id}")
    }

    fun publishAnsatt(ansatt: Ansatt) {
        val record = ProducerRecord(
            ANSATT_TOPIC,
            ansatt.id.toString(),
            objectMapper.writeValueAsString(ansatt.toDto()),
        )
        producerRecordStorage.store(ProducerUtils.serializeStringRecord(record))
        logger.info("La ansattmelding i Kafka-outbox for ansatt ${ansatt.id}")
    }

    private fun Arrangor.toDto() = ArrangorDto(
        id = id,
        source = AMT_ARRANGOR_SOURCE,
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId,
    )

    private fun Ansatt.toDto() = AnsattDto(
        id = id,
        source = AMT_ARRANGOR_SOURCE,
        personalia = personalia,
        arrangorer = arrangorer,
    )
}
