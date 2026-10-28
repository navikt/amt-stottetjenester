package no.nav.amt.person.service.kafka.producer

import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import no.nav.amt.person.service.kafka.producer.dto.ArrangorAnsattDtoV1
import no.nav.amt.person.service.kafka.producer.dto.NavAnsattDtoV1
import no.nav.amt.person.service.kafka.producer.dto.NavBrukerDtoV1
import no.nav.amt.person.service.kafka.producer.dto.NavEnhetDtoV1
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.util.ProducerUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Legger meldinger i Kafka-outboxen (kafka_producer_record).
 *
 * MANDATORY: Alle metoder må kalles i en aktiv transaksjon, slik at outbox-raden lagres
 * atomisk sammen med dataene den beskriver. Kaster IllegalTransactionStateException ellers.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class KafkaProducerService(
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val producerRecordStorage: KafkaProducerRecordStorage,
    private val objectMapper: ObjectMapper,
) {
    fun publiserNavBruker(navBruker: NavBrukerDbo) {
        store(
            ProducerRecord(
                kafkaTopicProperties.amtNavBrukerTopic,
                navBruker.person.id.toString(),
                objectMapper.writeValueAsString(NavBrukerDtoV1.fromDbo(navBruker)),
            ),
        )
    }

    // Kalles bare ved endringer i personalia (ArrangorAnsattService.onPersonUpdate), ikke ved
    // tildeling av rollen. Se KDoc på ArrangorAnsattService.hentEllerOpprettAnsatt.
    fun publiserArrangorAnsatt(ansatt: PersonDbo) {
        store(
            ProducerRecord(
                kafkaTopicProperties.amtArrangorAnsattPersonaliaTopic,
                ansatt.id.toString(),
                objectMapper.writeValueAsString(ArrangorAnsattDtoV1.fromDbo(ansatt)),
            ),
        )
    }

    fun publiserNavAnsatt(ansatt: NavAnsattDbo) {
        store(
            ProducerRecord(
                kafkaTopicProperties.amtNavAnsattPersonaliaTopic,
                ansatt.id.toString(),
                objectMapper.writeValueAsString(NavAnsattDtoV1.fromDbo(ansatt)),
            ),
        )
    }

    fun publiserNavEnhet(navEnhet: NavEnhetDbo) {
        store(
            ProducerRecord(
                kafkaTopicProperties.amtNavEnhetTopic,
                navEnhet.id.toString(),
                objectMapper.writeValueAsString(NavEnhetDtoV1.fromDbo(navEnhet)),
            ),
        )
    }

    // brukes kun av tester
    internal fun publiserSlettNavBruker(personId: UUID) {
        store(
            ProducerRecord(
                kafkaTopicProperties.amtNavBrukerTopic,
                personId.toString(),
                null,
            ),
        )
    }

    private fun store(record: ProducerRecord<String, String>) {
        producerRecordStorage.store(ProducerUtils.serializeStringRecord(record))
    }
}
