package no.nav.enhetsregister.kafka

import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class KafkaOutboxPublisher(producerClient: KafkaProducerClient<ByteArray, ByteArray>) :
    BatchedKafkaProducerRecordPublisher(producerClient) {
    private val closed = CompletableFuture<Void>()
    private val started = CompletableFuture<Void>()

    fun processorStarted() {
        started.complete(null)
    }

    fun awaitStarted() {
        started.get(20, TimeUnit.SECONDS)
    }

    override fun close() {
        try {
            super.close()
            closed.complete(null)
        } catch (e: RuntimeException) {
            closed.completeExceptionally(e)
            throw e
        }
    }

    fun awaitClosed() {
        closed.get(20, TimeUnit.SECONDS)
    }
}
