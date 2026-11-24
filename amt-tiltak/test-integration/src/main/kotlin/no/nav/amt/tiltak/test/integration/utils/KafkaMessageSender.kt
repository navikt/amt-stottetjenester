package no.nav.amt.tiltak.test.integration.utils

import jakarta.annotation.PreDestroy
import no.nav.amt.tiltak.kafka.config.KafkaProperties
import no.nav.common.kafka.producer.KafkaProducerClientImpl
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true")
class KafkaMessageSender(
    properties: KafkaProperties,
    @Value($$"${app.env.amtTiltakTopic}")
    private val amtTiltakTopic: String,
) {
    private val kafkaProducer = KafkaProducerClientImpl<ByteArray, ByteArray>(properties.producer(), false)

    fun sendTilAmtTiltakTopic(jsonString: String) {
        kafkaProducer.send(ProducerRecord(amtTiltakTopic, UUID.randomUUID().toString().toByteArray(), jsonString.toByteArray()))
    }

    @PreDestroy
    fun close() {
        kafkaProducer.close()
    }
}
