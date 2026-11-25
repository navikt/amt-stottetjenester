package no.nav.amt.tiltak.test.integration.utils

import no.nav.common.kafka.producer.KafkaProducerClient
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true")
class KafkaMessageSender(
    @Qualifier("kafkaProducer")
    private val kafkaProducer: KafkaProducerClient<ByteArray, ByteArray>,
    @Value($$"${app.env.amtTiltakTopic}")
    private val amtTiltakTopic: String,
) {
    fun sendTilAmtTiltakTopic(jsonString: String) {
        kafkaProducer.send(ProducerRecord(amtTiltakTopic, UUID.randomUUID().toString().toByteArray(), jsonString.toByteArray()))
    }
}
