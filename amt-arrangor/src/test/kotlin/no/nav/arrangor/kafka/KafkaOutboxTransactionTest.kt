package no.nav.arrangor.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.KafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID

class KafkaOutboxTransactionTest(
    private val ansattService: AnsattService,
    private val ansattRepository: AnsattRepository,
    private val arrangorRepository: ArrangorRepository,
    private val producerService: ProducerService,
    private val producerRepository: PostgresJdbcTemplateProducerRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val meterRegistry: MeterRegistry,
) : IntegrationTest() {
    @Nested
    inner class `Når ansatt oppdateres` {
        @Test
        fun `lagrer ansattendring og outboxmelding atomisk`() {
            // Arrange
            val arrangor = testDatabase.insertArrangor()
            val ansatt = testDatabase.insertAnsatt(
                arrangorer = listOf(
                    testDatabase.ansattArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )
            val deltakerlisteId = UUID.randomUUID()
            val metricCountBefore = metricCount("amt_arrangor_lagt_til_som_koordinator")

            // Act
            val oppdatertAnsatt = ansattService.setKoordinatorForDeltakerliste(
                personident = ansatt.personident,
                arrangorId = arrangor.id,
                deltakerlisteId = deltakerlisteId,
            )

            // Assert
            val record = outboxRecords().single()
            record.topic shouldBe ANSATT_TOPIC
            record.key shouldBe ansatt.id.toString()
            objectMapper.readTree(record.value)["id"].asString() shouldBe ansatt.id.toString()
            oppdatertAnsatt.arrangorer.single().koordinator shouldBe listOf(deltakerlisteId)
            metricCount("amt_arrangor_lagt_til_som_koordinator") shouldBe metricCountBefore + 1
        }

        @Test
        fun `ruller tilbake ansattendring og outboxmelding sammen`() {
            // Arrange
            val arrangor = testDatabase.insertArrangor()
            val ansatt = testDatabase.insertAnsatt(
                arrangorer = listOf(
                    testDatabase.ansattArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )
            val deltakerlisteId = UUID.randomUUID()
            val metricCountBefore = metricCount("amt_arrangor_lagt_til_som_koordinator")

            // Act
            transactionTemplate.executeWithoutResult { status ->
                ansattService.setKoordinatorForDeltakerliste(
                    personident = ansatt.personident,
                    arrangorId = arrangor.id,
                    deltakerlisteId = deltakerlisteId,
                )
                status.setRollbackOnly()
            }

            // Assert
            ansattRepository
                .get(ansatt.id)
                ?.arrangorer
                ?.single()
                ?.koordinator shouldBe emptyList()
            pendingOutboxRecords() shouldBe 0
            metricCount("amt_arrangor_lagt_til_som_koordinator") shouldBe metricCountBefore
        }
    }

    @Nested
    inner class `Når produsenten kalles uten transaksjon` {
        @Test
        fun `kaster exception uten å legge melding i outbox`() {
            // Arrange
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())

            // Act & Assert
            shouldThrow<IllegalTransactionStateException> {
                producerService.publishAnsatt(
                    ansatt = Ansatt(
                        id = ansatt.id,
                        personalia = ansatt.toPersonalia(),
                        arrangorer = emptyList(),
                    ),
                )
            }
            pendingOutboxRecords() shouldBe 0
        }
    }

    @Nested
    inner class `Når arrangør oppdateres` {
        @Test
        fun `legger arrangørmelding i outbox sammen med databaseendringen`() {
            // Arrange
            val arrangor = ArrangorRepository.ArrangorDbo(
                id = UUID.randomUUID(),
                navn = "Testarrangør",
                organisasjonsnummer = "123456789",
                overordnetArrangorId = null,
            )
            // Act
            transactionTemplate.executeWithoutResult {
                val lagretArrangor = arrangorRepository.insertOrUpdate(arrangor)
                producerService.publishArrangor(arrangor = lagretArrangor.toDomain())
            }

            // Assert
            val record = outboxRecords().single()
            record.topic shouldBe ARRANGOR_TOPIC
            record.key shouldBe arrangor.id.toString()
            objectMapper.readTree(record.value)["id"].asString() shouldBe arrangor.id.toString()
            arrangorRepository.get(arrangor.id) shouldBe arrangor
        }

        @Test
        fun `ruller tilbake arrangørendring og outboxmelding sammen`() {
            // Arrange
            val arrangor = ArrangorRepository.ArrangorDbo(
                id = UUID.randomUUID(),
                navn = "Testarrangør",
                organisasjonsnummer = "987654321",
                overordnetArrangorId = null,
            )

            // Act
            transactionTemplate.executeWithoutResult { status ->
                val lagretArrangor = arrangorRepository.insertOrUpdate(arrangor)
                producerService.publishArrangor(arrangor = lagretArrangor.toDomain())
                status.setRollbackOnly()
            }

            // Assert
            arrangorRepository.get(arrangor.id) shouldBe null
            pendingOutboxRecords() shouldBe 0
        }
    }

    @Nested
    inner class `Når outbox-prosessoren kjører` {
        @Test
        fun `sletter melding når produsenten bekrefter publisering`() {
            // Arrange
            val recordId = storeOutboxRecord()
            val publisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
            every { publisher.publishStoredRecords(any()) } returns listOf(recordId)
            val processor = createProcessor(publisher)

            try {
                // Act
                processor.start()

                // Assert
                verify(timeout = 1_000) { publisher.publishStoredRecords(any()) }
                await().atMost(Duration.ofSeconds(1)).untilAsserted {
                    pendingOutboxRecords() shouldBe 0
                }
            } finally {
                processor.close()
            }
        }

        @Test
        fun `beholder melding når produsenten ikke bekrefter publisering`() {
            // Arrange
            storeOutboxRecord()
            val publisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
            every { publisher.publishStoredRecords(any()) } returns emptyList()
            val processor = createProcessor(publisher)

            try {
                // Act
                processor.start()

                // Assert
                verify(timeout = 1_000, atLeast = 1) { publisher.publishStoredRecords(any()) }
                pendingOutboxRecords() shouldBe 1
            } finally {
                processor.close()
            }
        }
    }

    private data class OutboxRecord(
        val topic: String,
        val key: String,
        val value: String,
    )

    private fun outboxRecords(): List<OutboxRecord> = jdbcTemplate.query(
        "SELECT topic, key, value FROM kafka_producer_record ORDER BY id",
    ) { rs, _ ->
        OutboxRecord(
            topic = rs.getString("topic"),
            key = checkNotNull(rs.getBytes("key")).decodeToString(),
            value = checkNotNull(rs.getBytes("value")).decodeToString(),
        )
    }

    private fun storeOutboxRecord(): Long = producerRepository.storeRecord(
        StoredProducerRecord(
            ANSATT_TOPIC,
            "outbox-key".toByteArray(),
            "outbox-value".toByteArray(),
            "[]",
        ),
    )

    private fun createProcessor(publisher: KafkaProducerRecordPublisher): KafkaProducerRecordProcessor = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(producerRepository)
        .withRecordPublisher(publisher)
        .withLeaderElectionClient(LeaderElectionClient { true })
        .withTopicWhitelist(listOf(ANSATT_TOPIC, ARRANGOR_TOPIC))
        .withRecordsBatchSize(100)
        .withPollTimeoutMs(10)
        .withErrorTimeoutMs(10)
        .withWaitingForLeaderTimeoutMs(10)
        .withShutdownHookEnabled(false)
        .build()

    private fun pendingOutboxRecords(): Int = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM kafka_producer_record",
        Int::class.java,
    ) ?: error("Klarte ikke å hente antall outbox-records")

    private fun metricCount(name: String): Double = meterRegistry.counter(name).count()
}
