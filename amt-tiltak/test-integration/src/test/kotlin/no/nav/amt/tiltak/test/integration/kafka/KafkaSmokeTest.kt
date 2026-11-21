package no.nav.amt.tiltak.test.integration.kafka

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.tiltak.core.port.DeltakerService
import no.nav.amt.tiltak.kafka.config.KafkaProperties
import no.nav.amt.tiltak.test.database.DbTestDataUtils
import no.nav.amt.tiltak.test.database.data.TestData.DELTAKER_1
import no.nav.amt.tiltak.test.integration.IntegrationTestBase
import no.nav.amt.tiltak.test.integration.utils.DeltakerMessage
import no.nav.amt.tiltak.test.integration.utils.KafkaMessageCreator
import no.nav.amt.tiltak.test.integration.utils.KafkaMessageSender
import no.nav.amt.tiltak.test.utils.AsyncUtils.eventually
import no.nav.common.kafka.producer.KafkaProducerClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("kafka-smoke")
@TestPropertySource(properties = ["kafka.enabled=true"])
class KafkaSmokeTest(
    private val messageSender: KafkaMessageSender,
    private val deltakerService: DeltakerService,
    private val kafkaProperties: KafkaProperties,
    private val producerClient: KafkaProducerClient<ByteArray, ByteArray>,
    @Value($$"${app.env.deltakerTopic}")
    private val outputTopic: String,
) : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        DbTestDataUtils.cleanAndInitDatabaseWithTestData(dataSource)
        resetMockServersAndAddDefaultData()
    }

    @Test
    fun `innkommende Kafka-melding sletter deltaker gjennom applikasjonens consumer`() {
        deltakerService.hentDeltaker(DELTAKER_1.id).shouldNotBeNull()
        val message = DeltakerMessage(
            id = DELTAKER_1.id,
            gjennomforingId = DELTAKER_1.gjennomforingId,
            operation = "DELETED",
        )

        messageSender.sendTilAmtTiltakTopic(KafkaMessageCreator.opprettAmtTiltakDeltakerMessage(message))

        eventually {
            deltakerService.hentDeltaker(DELTAKER_1.id) shouldBe null
        }
    }

    @Test
    fun `publiserer og leser en record med ekte Kafka-klienter`() {
        val key = UUID.randomUUID().toString()
        val value = """{"type":"kafka-smoke","id":"$key"}"""
        val properties = kafkaProperties.consumer().apply {
            setProperty(ConsumerConfig.GROUP_ID_CONFIG, "kafka-smoke-$key")
            setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }

        KafkaConsumer(properties, StringDeserializer(), StringDeserializer()).use { consumer ->
            consumer.subscribe(listOf(outputTopic))
            eventually {
                consumer.poll(Duration.ofMillis(100))
                consumer.assignment().isNotEmpty() shouldBe true
            }

            val metadata = producerClient
                .send(
                    ProducerRecord(
                        outputTopic,
                        null,
                        key.toByteArray(Charsets.UTF_8),
                        value.toByteArray(Charsets.UTF_8),
                        listOf(RecordHeader("kafka-smoke", "roundtrip".toByteArray(Charsets.UTF_8))),
                    ),
                ).get(10, TimeUnit.SECONDS)
            metadata.topic() shouldBe outputTopic

            var received: ConsumerRecord<String, String>? = null
            eventually {
                if (received == null) {
                    received = consumer.poll(Duration.ofMillis(100)).firstOrNull { it.key() == key }
                }
                val record = received.shouldNotBeNull()
                record.key() shouldBe key
                record.value() shouldBe value
                record
                    .headers()
                    .lastHeader("kafka-smoke")
                    .shouldNotBeNull()
                    .value()
                    .toString(Charsets.UTF_8) shouldBe "roundtrip"
            }
        }
    }
}
