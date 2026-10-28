package no.nav.arrangor.kafka

import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxLifecycle(
    private val producerRecordProcessor: KafkaProducerRecordProcessor,
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var running = false

    override fun start() {
        if (running) return

        logger.info("Starting Kafka outbox processor...")
        producerRecordProcessor.start()
        running = true
    }

    override fun stop() {
        if (!running) return

        logger.info("Stopping Kafka outbox processor...")
        producerRecordProcessor.close()
        running = false
    }

    override fun isRunning() = running
}
