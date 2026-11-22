package no.nav.amt.arena.acl.kafka

import no.nav.common.kafka.consumer.KafkaConsumerClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaConsumer(
    @Qualifier("arenaKafkaConsumerClient")
    private val client: KafkaConsumerClient,
    @Qualifier("arenaTempKafkaConsumerClient")
    private val tempClient: KafkaConsumerClient,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    // Sikrer at isRunning() ser statusendringer fra start()/stop() ved kall fra ulike threads.
    @Volatile
    private var running = false

    override fun start() {
        if (running) return

        log.info("Starting Kafka consumer...")
        client.start()
        tempClient.start()
        running = true
    }

    override fun stop() {
        if (!running) return

        log.info("Stopping Kafka consumer...")
        try {
            var failure: IllegalStateException? = null
            for (closeAction in listOf<() -> Unit>(client::close, tempClient::close)) {
                try {
                    closeAction()
                } catch (e: IllegalStateException) {
                    val firstFailure = failure
                    if (firstFailure == null) {
                        failure = e
                    } else {
                        firstFailure.addSuppressed(e)
                    }
                }
            }
            failure?.let { throw it }
        } finally {
            running = false
        }
    }

    override fun stop(callback: Runnable) {
        try {
            stop()
        } finally {
            callback.run()
        }
    }

    override fun isRunning() = running
}
