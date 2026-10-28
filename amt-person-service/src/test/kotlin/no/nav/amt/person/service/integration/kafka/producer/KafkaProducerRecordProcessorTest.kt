package no.nav.amt.person.service.integration.kafka.producer

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.KafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KafkaProducerRecordProcessorTest(
    private val repository: PostgresJdbcTemplateProducerRepository,
) : IntegrationTestBase() {
    private val publisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
    private val leaderElectionClient = mockk<LeaderElectionClient>()

    @Nested
    inner class Start {
        @Test
        fun `bekreftet publisering sletter outbox-raden`() {
            // Arrange
            val id = storeRecord()
            every { leaderElectionClient.isLeader } returns true
            every { publisher.publishStoredRecords(any()) } returns listOf(id)
            val processor = processor()

            try {
                // Act
                processor.start()

                // Assert
                awaitOutboxCount(0) shouldBe true
            } finally {
                processor.close()
                verify(timeout = 1_000) { publisher.close() }
            }
        }

        @Test
        fun `publiseringsfeil beholder outbox-raden for nytt forsøk`() {
            // Arrange
            storeRecord()
            every { leaderElectionClient.isLeader } returns true
            every { publisher.publishStoredRecords(any()) } returns emptyList()
            val processor = processor()

            try {
                // Act
                processor.start()

                // Assert
                verify(timeout = 1_000, atLeast = 1) { publisher.publishStoredRecords(any()) }
            } finally {
                processor.close()
                verify(timeout = 1_000) { publisher.close() }
            }

            // Assert
            outboxRecords().size shouldBe 1
        }
    }

    private fun storeRecord(): Long = repository.storeRecord(
        StoredProducerRecord(
            "topic",
            "key".toByteArray(),
            "value".toByteArray(),
            "[]",
        ),
    )

    private fun processor(): KafkaProducerRecordProcessor = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(repository)
        .withRecordPublisher(publisher)
        .withLeaderElectionClient(leaderElectionClient)
        .withRecordsBatchSize(100)
        .withPollTimeoutMs(10)
        .withErrorTimeoutMs(10)
        .withWaitingForLeaderTimeoutMs(10)
        .withShutdownHookEnabled(false)
        .build()

    private fun awaitOutboxCount(expectedCount: Int): Boolean {
        repeat(100) {
            if (outboxRecords().size == expectedCount) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
}
