package no.nav.arrangor.kafka

import no.nav.arrangor.MetricsService
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.KafkaProducerRecordPublisher

/**
 * Teller bare outbox-meldinger som delegaten bekrefter at er publisert.
 *
 * Returnerer de bekreftede ID-ene uendret, slik at outbox-prosessoren kan slette meldingene.
 */
class MetricsKafkaProducerRecordPublisher(
    private val delegate: KafkaProducerRecordPublisher,
    private val metricsService: MetricsService,
) : KafkaProducerRecordPublisher {
    override fun publishStoredRecords(records: List<StoredProducerRecord>): List<Long> {
        val publishedIds = delegate.publishStoredRecords(records)
        val publishedIdSet = publishedIds.toSet()

        records
            .filter { it.id in publishedIdSet }
            .groupingBy { it.topic }
            .eachCount()
            .forEach { (topic, count) ->
                when (topic) {
                    ANSATT_TOPIC -> metricsService.incPubliserteAnsatte(count = count)
                    ARRANGOR_TOPIC -> metricsService.incPubliserteArrangorer(count = count)
                }
            }

        return publishedIds
    }

    override fun close() = delegate.close()
}
