package no.nav.enhetsregister.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.KafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import no.nav.enhetsregister.client.BronnoysundClient
import no.nav.enhetsregister.domain.EnhetOppdatering
import no.nav.enhetsregister.domain.EnhetOppdateringType
import no.nav.enhetsregister.domain.EnhetType
import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import no.nav.enhetsregister.repository.DeltaOppdateringProgresjonRepository
import no.nav.enhetsregister.repository.EnhetRepository
import no.nav.enhetsregister.repository.RepositoryTestBase
import no.nav.enhetsregister.service.DeltaOppdateringEnhetService
import no.nav.enhetsregister.service.EnhetService
import no.nav.enhetsregister.service.KafkaProducerService
import no.nav.enhetsregister.service.OppdaterAlleEnheterService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(
    classes = [
        EnhetRepository::class,
        DeltaOppdateringProgresjonRepository::class,
        EnhetService::class,
        DeltaOppdateringEnhetService::class,
        OppdaterAlleEnheterService::class,
        KafkaProducerService::class,
        KafkaOutboxStorageConfig::class,
        KafkaOutboxMetrics::class,
        KafkaOutboxTransactionTest.Config::class,
    ],
    properties = [
        "kafka.enabled=false",
        "spring.flyway.schemas=outbox_transactions",
        "spring.flyway.default-schema=outbox_transactions",
        "spring.datasource.hikari.schema=outbox_transactions",
    ],
)
class KafkaOutboxTransactionTest(
    private val enhetService: EnhetService,
    private val enhetRepository: EnhetRepository,
    private val deltaService: DeltaOppdateringEnhetService,
    private val bulkService: OppdaterAlleEnheterService,
    private val progresjonRepository: DeltaOppdateringProgresjonRepository,
    private val producerService: KafkaProducerService,
    private val producerRepository: PostgresJdbcTemplateProducerRepository,
    private val bronnoysundClient: BronnoysundClient,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val meterRegistry: MeterRegistry,
) : RepositoryTestBase() {
    @BeforeEach
    fun setup() {
        clearMocks(bronnoysundClient)
        jdbcTemplate.execute("TRUNCATE TABLE enhet, kafka_producer_record, delta_enhet_oppdatering RESTART IDENTITY")
        jdbcTemplate.update(
            "INSERT INTO delta_enhet_oppdatering (enhet_type, oppdatering_id) VALUES ('MODERENHET', 33), ('UNDERENHET', 33)",
        )
    }

    @Test
    fun `entity and exact outbox payload commit together`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "Testenhet", "987654321")))

        assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("Testenhet")
        val record = producerRepository.getRecords(100).single()
        assertThat(record.topic).isEqualTo(TOPIC)
        assertThat(record.key.decodeToString()).isEqualTo("123456789")
        assertThat(record.value.decodeToString()).isEqualTo(
            """{"organisasjonsnummer":"123456789","navn":"Testenhet","overordnetEnhetOrganisasjonsnummer":"987654321"}""",
        )
        assertThat(pendingGauge()).isEqualTo(1.0)
    }

    @Test
    fun `rollback restores entity and removes its new outbox record`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "Original")))
        producerRepository.deleteRecords(producerRepository.getRecords(100).map { it.id })

        transactionTemplate.executeWithoutResult {
            enhetService.oppdaterEnheter(listOf(upsert("123456789", "Changed"), upsert("987654321", "New")))
            it.setRollbackOnly()
        }

        assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("Original")
        assertThat(enhetRepository.hentEnhet("987654321")).isNull()
        assertThat(pendingGauge()).isZero()
    }

    @Test
    fun `producer requires a real Spring transaction`() {
        assertThatThrownBy {
            producerService.publiserVirksomhet(VirksomhetV1Dto("123456789", "Testenhet", null))
        }.isInstanceOf(IllegalTransactionStateException::class.java)
        assertThat(pendingGauge()).isZero()
    }

    @Test
    fun `serialization failure rolls back the entity update`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "Original")))
        producerRepository.deleteRecords(producerRepository.getRecords(100).map { it.id })
        val failingMapper = mockk<ObjectMapper>()
        every { failingMapper.writeValueAsBytes(any()) } throws IllegalStateException("Serialization failed")
        val failingProducer = KafkaProducerService(
            KafkaProducerRecordStorage(producerRepository),
            KafkaTopicProperties(TOPIC),
            failingMapper,
        )
        val service = EnhetService(enhetRepository, bronnoysundClient, failingProducer, transactionTemplate)

        assertThatThrownBy { service.oppdaterEnheter(listOf(upsert("123456789", "Changed"))) }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("Original")
        assertThat(pendingGauge()).isZero()
    }

    @ParameterizedTest
    @EnumSource(EnhetType::class)
    fun `delta page commits both records and its next cursor`(type: EnhetType) {
        stubPage(type)

        updateDelta(type)

        assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("First")
        assertThat(enhetRepository.hentEnhet("987654321")?.navn).isEqualTo("Second")
        assertThat(pendingGauge()).isEqualTo(2.0)
        assertThat(progresjonRepository.hentOppdateringProgresjon(type).oppdateringId).isEqualTo(35)
    }

    @ParameterizedTest
    @EnumSource(EnhetType::class)
    fun `outbox failure on second entity rolls back the page and retry succeeds`(type: EnhetType) {
        stubPage(type)
        jdbcTemplate.execute(
            "ALTER TABLE kafka_producer_record ADD CONSTRAINT reject_second CHECK (convert_from(value, 'UTF8') NOT LIKE '%Second%')",
        )
        try {
            assertThatThrownBy { updateDelta(type) }.isInstanceOf(DataIntegrityViolationException::class.java)
            assertUncommittedPage(type)
        } finally {
            jdbcTemplate.execute("ALTER TABLE kafka_producer_record DROP CONSTRAINT reject_second")
        }

        updateDelta(type)
        assertThat(pendingGauge()).isEqualTo(2.0)
        assertThat(progresjonRepository.hentOppdateringProgresjon(type).oppdateringId).isEqualTo(35)
    }

    @ParameterizedTest
    @EnumSource(EnhetType::class)
    fun `cursor failure rolls back all entity and outbox writes`(type: EnhetType) {
        stubPage(type)
        jdbcTemplate.execute("ALTER TABLE delta_enhet_oppdatering ADD CONSTRAINT reject_cursor CHECK (oppdatering_id = 33)")
        try {
            assertThatThrownBy { updateDelta(type) }.isInstanceOf(DataIntegrityViolationException::class.java)
            assertUncommittedPage(type)
        } finally {
            jdbcTemplate.execute("ALTER TABLE delta_enhet_oppdatering DROP CONSTRAINT reject_cursor")
        }
        updateDelta(type)
        assertThat(pendingGauge()).isEqualTo(2.0)
    }

    @ParameterizedTest
    @EnumSource(EnhetType::class)
    fun `empty delta page does not advance the cursor`(type: EnhetType) {
        stubPage(type, emptyList())
        updateDelta(type)
        assertUncommittedPage(type)
    }

    @Test
    fun `mapping failure leaves the page uncommitted`() {
        stubPage(EnhetType.MODERENHET)
        every { bronnoysundClient.hentModerenhet("987654321") } throws IllegalStateException("Upstream failed")
        assertThatThrownBy { deltaService.deltaOppdaterModerenheter() }.isInstanceOf(IllegalStateException::class.java)
        assertUncommittedPage(EnhetType.MODERENHET)
    }

    @Test
    fun `entity failure after the first write rolls back the page`() {
        stubPage(EnhetType.MODERENHET)
        jdbcTemplate.execute("ALTER TABLE enhet ADD CONSTRAINT reject_entity CHECK (navn <> 'Second')")
        try {
            assertThatThrownBy { deltaService.deltaOppdaterModerenheter() }
                .isInstanceOf(DataIntegrityViolationException::class.java)
            assertUncommittedPage(EnhetType.MODERENHET)
        } finally {
            jdbcTemplate.execute("ALTER TABLE enhet DROP CONSTRAINT reject_entity")
        }
    }

    @Test
    fun `missing checkpoint row fails and rolls back the page`() {
        stubPage(EnhetType.MODERENHET)
        every { bronnoysundClient.hentModerenhet("987654321") } answers {
            assertNoTransaction()
            jdbcTemplate.update("DELETE FROM delta_enhet_oppdatering WHERE enhet_type = 'MODERENHET'")
            Moderenhet("987654321", "Second", null)
        }
        assertThatThrownBy { deltaService.deltaOppdaterModerenheter() }.isInstanceOf(IllegalStateException::class.java)
        assertThat(enhetRepository.hentEnhet("123456789")).isNull()
        assertThat(enhetRepository.hentEnhet("987654321")).isNull()
        assertThat(pendingGauge()).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["removed", "deleted", "missing-parent"])
    fun `delta mapping preserves stored parent without fallback HTTP`(scenario: String) {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "Original", "987654321")))
        producerRepository.deleteRecords(producerRepository.getRecords(100).map { it.id })
        val updates = listOf(update("123456789", 33))
        every { bronnoysundClient.hentUnderenhetOppdateringer(33, 500) } returns updates
        every { bronnoysundClient.hentUnderenhet("123456789") } returns when (scenario) {
            "removed" -> null
            "deleted" -> Underenhet("123456789", "Deleted", "2026-01-01", null)
            else -> Underenhet("123456789", "Missing parent", null, null)
        }

        deltaService.deltaOppdaterUnderenheter()

        assertThat(enhetRepository.hentEnhet("123456789")?.overordnetEnhet).isEqualTo("987654321")
        assertThat(pendingGauge()).isEqualTo(1.0)
        verify(exactly = 1) { bronnoysundClient.hentUnderenhet("123456789") }
        verify(exactly = 0) { bronnoysundClient.hentModerenhet(any()) }
    }

    @Test
    fun `missing stored parent does not create an entity during mapping`() {
        every { bronnoysundClient.hentUnderenhetOppdateringer(33, 500) } returns listOf(update("123456789", 33))
        every { bronnoysundClient.hentUnderenhet("123456789") } returns null
        deltaService.deltaOppdaterUnderenheter()
        assertThat(enhetRepository.hentEnhet("123456789")?.overordnetEnhet).isNull()
        assertThat(pendingGauge()).isEqualTo(1.0)
        verify(exactly = 1) { bronnoysundClient.hentUnderenhet("123456789") }
        verify(exactly = 0) { bronnoysundClient.hentModerenhet(any()) }
    }

    @Test
    fun `API fallback persists entity and queues its message without a transaction around HTTP`() {
        every { bronnoysundClient.hentUnderenhet("123456789") } answers {
            assertNoTransaction()
            null
        }
        every { bronnoysundClient.hentModerenhet("123456789") } answers {
            assertNoTransaction()
            Moderenhet("123456789", "Fallback", null)
        }

        assertThat(enhetService.hentEnhet("123456789")?.navn).isEqualTo("Fallback")
        assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("Fallback")
        assertThat(pendingGauge()).isEqualTo(1.0)
    }

    @ParameterizedTest
    @EnumSource(EnhetType::class)
    fun `bulk imports retain earlier per-entity commits when later publication fails`(type: EnhetType) {
        every { bronnoysundClient.hentAlleModerenheter() } answers {
            assertNoTransaction()
            listOf(Moderenhet("123456789", "First", null), Moderenhet("987654321", "Second", null))
        }
        every { bronnoysundClient.hentAlleUnderenheter() } answers {
            assertNoTransaction()
            listOf(Underenhet("123456789", "First", null, null), Underenhet("987654321", "Second", null, "123456789"))
        }
        jdbcTemplate.execute(
            "ALTER TABLE kafka_producer_record ADD CONSTRAINT reject_second CHECK (convert_from(value, 'UTF8') NOT LIKE '%Second%')",
        )
        try {
            assertThatThrownBy {
                if (type == EnhetType.MODERENHET) bulkService.oppdaterAlleModerenheter() else bulkService.oppdaterAlleUnderenheter()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
            assertThat(enhetRepository.hentEnhet("123456789")?.navn).isEqualTo("First")
            assertThat(enhetRepository.hentEnhet("987654321")).isNull()
            assertThat(pendingGauge()).isEqualTo(1.0)
        } finally {
            jdbcTemplate.execute("ALTER TABLE kafka_producer_record DROP CONSTRAINT reject_second")
        }

        if (type == EnhetType.MODERENHET) bulkService.oppdaterAlleModerenheter() else bulkService.oppdaterAlleUnderenheter()
        val records = producerRepository.getRecords(100)
        assertThat(records.map { it.key.decodeToString() }).containsExactly("123456789", "123456789", "987654321")
        val expectedParent = if (type == EnhetType.MODERENHET) "null" else "\"123456789\""
        assertThat(records.last().value.decodeToString()).isEqualTo(
            """{"organisasjonsnummer":"987654321","navn":"Second","overordnetEnhetOrganisasjonsnummer":$expectedParent}""",
        )
    }

    @Test
    fun `processor retains failed records and drains them after recovery`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "First")))
        val recovered = AtomicBoolean(false)
        val publisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
        every { publisher.publishStoredRecords(any()) } answers {
            if (!recovered.get()) throw IllegalStateException("Publication failed")
            firstArg<List<StoredProducerRecord>>().map { it.id }
        }
        val processor = processor(publisher)
        try {
            processor.start()
            verify(timeout = 2_000) { publisher.publishStoredRecords(any()) }
            assertThat(pendingGauge()).isEqualTo(1.0)
            recovered.set(true)
            await().atMost(Duration.ofSeconds(2)).untilAsserted { assertThat(pendingGauge()).isZero() }
        } finally {
            closeProcessor(processor, publisher)
        }
    }

    @Test
    fun `unacknowledged record survives processor restart`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "First")))
        val failedPublisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
        every { failedPublisher.publishStoredRecords(any()) } returns emptyList()
        val firstProcessor = processor(failedPublisher)
        try {
            firstProcessor.start()
            verify(timeout = 2_000) { failedPublisher.publishStoredRecords(any()) }
            assertThat(pendingGauge()).isEqualTo(1.0)
        } finally {
            closeProcessor(firstProcessor, failedPublisher)
        }
        val successfulPublisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
        every { successfulPublisher.publishStoredRecords(any()) } answers { firstArg<List<StoredProducerRecord>>().map { it.id } }
        val secondProcessor = processor(successfulPublisher)
        try {
            secondProcessor.start()
            await().atMost(Duration.ofSeconds(2)).untilAsserted { assertThat(pendingGauge()).isZero() }
        } finally {
            closeProcessor(secondProcessor, successfulPublisher)
        }
    }

    @Test
    fun `processor only removes acknowledged whitelisted records while leader`() {
        enhetService.oppdaterEnheter(listOf(upsert("123456789", "First"), upsert("987654321", "Second")))
        producerRepository.storeRecord(StoredProducerRecord("other-topic", byteArrayOf(), byteArrayOf(), "[]"))
        val leader = AtomicBoolean(false)
        val election = mockk<LeaderElectionClient>()
        every { election.isLeader } answers { leader.get() }
        val publisher = mockk<KafkaProducerRecordPublisher>(relaxUnitFun = true)
        every { publisher.publishStoredRecords(any()) } answers {
            leader.set(false)
            val records = firstArg<List<StoredProducerRecord>>()
            assertThat(records.map { it.topic }).containsOnly(TOPIC)
            listOf(records.first().id)
        }
        val processor = processor(publisher, election)
        try {
            processor.start()
            verify(timeout = 2_000) { election.isLeader }
            verify(exactly = 0) { publisher.publishStoredRecords(any()) }
            assertThat(pendingGauge()).isEqualTo(3.0)
            leader.set(true)
            await().atMost(Duration.ofSeconds(2)).untilAsserted { assertThat(pendingGauge()).isEqualTo(2.0) }
        } finally {
            closeProcessor(processor, publisher)
        }
    }

    private fun processor(
        publisher: KafkaProducerRecordPublisher,
        leader: LeaderElectionClient = LeaderElectionClient { true },
    ): KafkaProducerRecordProcessor = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(producerRepository)
        .withRecordPublisher(publisher)
        .withLeaderElectionClient(leader)
        .withTopicWhitelist(listOf(TOPIC))
        .withPollTimeoutMs(10)
        .withErrorTimeoutMs(10)
        .withWaitingForLeaderTimeoutMs(10)
        .withShutdownHookEnabled(false)
        .build()

    private fun closeProcessor(processor: KafkaProducerRecordProcessor, publisher: KafkaProducerRecordPublisher) {
        processor.close()
        verify(timeout = 2_000) { publisher.close() }
    }

    private fun assertUncommittedPage(type: EnhetType) {
        assertThat(enhetRepository.hentEnhet("123456789")).isNull()
        assertThat(enhetRepository.hentEnhet("987654321")).isNull()
        assertThat(pendingGauge()).isZero()
        assertThat(progresjonRepository.hentOppdateringProgresjon(type).oppdateringId).isEqualTo(33)
    }

    private fun assertNoTransaction() = assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()

    private fun pendingGauge() = meterRegistry.get("amt_enhetsregister_kafka_outbox_ventende").gauge().value()

    private fun updateDelta(type: EnhetType) {
        if (type == EnhetType.MODERENHET) deltaService.deltaOppdaterModerenheter() else deltaService.deltaOppdaterUnderenheter()
    }

    private fun stubPage(type: EnhetType, updates: List<EnhetOppdatering> = listOf(update("123456789", 33), update("987654321", 34))) {
        if (type == EnhetType.MODERENHET) {
            every { bronnoysundClient.hentModerenhetOppdateringer(33, 500) } answers {
                assertNoTransaction()
                updates
            }
        } else {
            every { bronnoysundClient.hentUnderenhetOppdateringer(33, 500) } answers {
                assertNoTransaction()
                updates
            }
        }
        listOf("123456789" to "First", "987654321" to "Second").forEach { (orgnr, name) ->
            every { bronnoysundClient.hentModerenhet(orgnr) } answers {
                assertNoTransaction()
                Moderenhet(orgnr, name, null)
            }
            every { bronnoysundClient.hentUnderenhet(orgnr) } answers {
                assertNoTransaction()
                Underenhet(orgnr, name, null, null)
            }
        }
    }

    private fun update(orgnr: String, id: Int) = EnhetOppdatering(id, ZonedDateTime.now(), orgnr, EnhetOppdateringType.ENDRING)

    private fun upsert(orgnr: String, name: String, parent: String? = null) = EnhetService.UpsertEnhet(orgnr, name, parent)

    @TestConfiguration
    @EnableTransactionManagement
    class Config {
        @Bean
        fun bronnoysundClient(): BronnoysundClient = mockk()

        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

        @Bean
        fun kafkaTopicProperties() = KafkaTopicProperties(TOPIC)
    }

    companion object {
        private const val TOPIC = "virksomhetertopic"
    }
}
