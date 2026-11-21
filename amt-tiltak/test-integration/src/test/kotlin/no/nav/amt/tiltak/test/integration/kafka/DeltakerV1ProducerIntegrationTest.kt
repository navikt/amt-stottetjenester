package no.nav.amt.tiltak.test.integration.kafka

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.amt.tiltak.core.kafka.DeltakerV1ProducerService
import no.nav.amt.tiltak.core.port.DeltakerService
import no.nav.amt.tiltak.kafka.config.KafkaTopicProperties
import no.nav.amt.tiltak.test.database.DbTestDataUtils
import no.nav.amt.tiltak.test.database.data.TestData.DELTAKER_1
import no.nav.amt.tiltak.test.integration.IntegrationTestBase
import no.nav.common.kafka.producer.KafkaProducerClient
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeltakerV1ProducerIntegrationTest(
    private val kafkaProducer: KafkaProducerClient<ByteArray, ByteArray>,
    private val deltakerService: DeltakerService,
    private val deltakerV1ProducerService: DeltakerV1ProducerService,
    private val kafkaTopicProperties: KafkaTopicProperties,
) : IntegrationTestBase() {
    @BeforeEach
    internal fun setUp() {
        DbTestDataUtils.cleanAndInitDatabaseWithTestData(dataSource)
        clearMocks(kafkaProducer, answers = false)
    }

    @Test
    fun `skal publisere deltaker med riktig key og value`() {
        val deltaker = deltakerService.hentDeltaker(DELTAKER_1.id)!!
        deltakerV1ProducerService.publiserDeltaker(deltaker, deltaker.endretDato)

        val expectedJson =
            """
            {"id":"dc600c70-124f-4fe7-a687-b58439beb214","gjennomforingId":"b3420940-5479-48c8-b2fa-3751c7a33aa2","personIdent":"12345678910","startDato":"2022-02-13","sluttDato":"2030-02-14","status":{"type":"DELTAR","statusTekst":"Deltar","aarsak":null,"aarsakTekst":null,"opprettetDato":"2022-02-13T00:00:00"},"registrertDato":"2022-02-13T12:12:00","dagerPerUke":2.5,"prosentStilling":100.0,"endretDato":"2022-02-13T12:12:00","kilde":"ARENA"}
            """.trimIndent()

        val record = slot<ProducerRecord<ByteArray, ByteArray>>()
        verify(exactly = 1) { kafkaProducer.sendSync(capture(record)) }
        val deltakerRecord = record.captured
        deltakerRecord.topic() shouldBe kafkaTopicProperties.deltakerTopic
        deltakerRecord.key().toString(Charsets.UTF_8) shouldBe DELTAKER_1.id.toString()
        deltakerRecord.value()?.toString(Charsets.UTF_8) shouldBe expectedJson
    }

    @Test
    fun `skal publisere sletting av deltaker med riktig key og null value`() {
        deltakerV1ProducerService.publiserSlettDeltaker(DELTAKER_1.id)

        val record = slot<ProducerRecord<ByteArray, ByteArray>>()
        verify(exactly = 1) { kafkaProducer.sendSync(capture(record)) }
        val deltakerRecord = record.captured
        deltakerRecord.topic() shouldBe kafkaTopicProperties.deltakerTopic
        deltakerRecord.key().toString(Charsets.UTF_8) shouldBe DELTAKER_1.id.toString()
        deltakerRecord.value() shouldBe null
    }
}
