package no.nav.amt.arena.acl.integration.kafka

import no.nav.amt.arena.acl.kafka.KafkaTopicProperties
import no.nav.amt.arena.acl.services.ArenaMessageConsumerService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "false")
class ArenaMessageTestSender(
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val arenaMessageConsumerService: ArenaMessageConsumerService,
) {
    private val offset = AtomicLong()

    fun publiserArenaGjennomforing(
        arenaGjennomforingId: Long,
        jsonMessage: String,
    ) = process(kafkaTopicProperties.arenaTiltakGjennomforingTopic, arenaGjennomforingId, jsonMessage)

    fun publiserArenaDeltaker(
        arenaDeltakerId: Long,
        jsonMessage: String,
    ) = process(kafkaTopicProperties.arenaTiltakDeltakerTopic, arenaDeltakerId, jsonMessage)

    fun publiserArenaHistDeltaker(
        arenaDeltakerId: Long,
        jsonMessage: String,
    ) = process(kafkaTopicProperties.arenaHistTiltakDeltakerTopic, arenaDeltakerId, jsonMessage)

    private fun process(
        topic: String,
        arenaId: Long,
        jsonMessage: String,
    ) = arenaMessageConsumerService.handleArenaGoldenGateRecord(
        ConsumerRecord(topic, 0, offset.getAndIncrement(), arenaId.toString(), jsonMessage),
    )
}
