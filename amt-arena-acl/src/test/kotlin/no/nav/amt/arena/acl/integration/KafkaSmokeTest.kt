package no.nav.amt.arena.acl.integration

import com.ninjasquad.springmockk.MockkSpyBean
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing as MulighetsrommetGjennomforing
import no.nav.amt.arena.acl.domain.db.IngestStatus
import no.nav.amt.arena.acl.domain.kafka.amt.AmtOperation
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageCreator
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageSender
import no.nav.amt.arena.acl.kafka.KafkaProperties
import no.nav.amt.arena.acl.kafka.KafkaTopicProperties
import no.nav.amt.arena.acl.repositories.ArenaDataRepository
import no.nav.amt.arena.acl.services.GjennomforingService
import no.nav.amt.arena.acl.services.KafkaProducerService
import no.nav.amt.arena.acl.services.ArenaConsumerServiceTemp
import no.nav.amt.arena.acl.services.ArenaMessageConsumerService
import no.nav.amt.arena.acl.utils.ARENA_DELTAKER_TABLE_NAME
import no.nav.amt.arena.acl.utils.ARENA_GJENNOMFORING_TABLE_NAME
import no.nav.amt.arena.acl.utils.JsonUtils.objectMapper
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

@Tag("kafka-smoke")
@TestPropertySource(properties = ["kafka.enabled=true"])
@MockkSpyBean(types = [ArenaConsumerServiceTemp::class, ArenaMessageConsumerService::class])
class KafkaSmokeTest(
    private val messageSender: KafkaMessageSender,
    private val gjennomforingService: GjennomforingService,
    private val arenaDataRepository: ArenaDataRepository,
    private val producerService: KafkaProducerService,
    private val kafkaProperties: KafkaProperties,
    private val kafkaTopicProperties: KafkaTopicProperties,
    private val arenaConsumerServiceTemp: ArenaConsumerServiceTemp,
    private val arenaMessageConsumerService: ArenaMessageConsumerService,
    @Value($$"${app.env.amtEnkeltplassDeltakerTopic}")
    private val outputTopic: String,
) : IntegrationTestBase() {
    @Test
    fun `Kafka-melding blir behandlet av applikasjonen og lagret i PostgreSQL`() {
        val arenaId = Random.nextLong(1, Long.MAX_VALUE)
        val gjennomforingId = UUID.randomUUID()
        val position = Random.nextLong(1, Long.MAX_VALUE).toString()
        val gjennomforing = KafkaMessageCreator.baseGjennomforing(
            arenaGjennomforingId = arenaId,
            arbgivIdArrangor = 68968L,
            datoFra = LocalDateTime.now().minusDays(3),
            datoTil = LocalDateTime.now().plusDays(3),
        )
        mockArenaOrdsProxyHttpServer.mockHentVirksomhetsnummer(
            arenaArbeidsgiverId = "68968",
            virksomhetsnummer = "12345",
        )
        mockMulighetsrommetApiServer.mockHentGjennomforingId(arenaId, gjennomforingId)
        val completed = CountDownLatch(1)
        every {
            arenaMessageConsumerService.handleArenaGoldenGateRecord(
                match { it.key() == arenaId.toString() && it.topic() == kafkaTopicProperties.arenaTiltakGjennomforingTopic },
            )
        } answers {
            callOriginal()
            completed.countDown()
        }

        messageSender.publiserArenaGjennomforing(
            arenaId,
            objectMapper.writeValueAsString(
                KafkaMessageCreator.opprettArenaGjennomforingMessage(gjennomforing, opPos = position),
            ),
        )

        await().untilAsserted {
            completed.count shouldBe 0L
            val result = gjennomforingService.get(arenaId.toString()).shouldNotBeNull()
            result.isValid shouldBe true
            result.isSupported shouldBe true
            result.id shouldBe gjennomforingId
            val record = arenaDataRepository
                .get(
                    ARENA_GJENNOMFORING_TABLE_NAME,
                    AmtOperation.CREATED,
                    position,
                ).shouldNotBeNull()
            record.ingestStatus shouldBe IngestStatus.HANDLED
        }
    }

    @Test
    fun `deltakermelding leveres til den midlertidige konsumenten gjennom ekte Kafka`() {
        val deltaker = KafkaMessageCreator.baseDeltaker(
            startDato = LocalDate.now().plusDays(1),
            deltakerStatusKode = "AKTUELL",
        )
        val gjennomforingId = UUID.randomUUID()
        val position = Random.nextLong(1, Long.MAX_VALUE).toString()
        gjennomforingService.upsert(deltaker.TILTAKGJENNOMFORING_ID.toString(), "ENKELAMO", true)
        gjennomforingService.setGjennomforingId(deltaker.TILTAKGJENNOMFORING_ID.toString(), gjennomforingId)
        mockArenaOrdsProxyHttpServer.mockHentFnr(deltaker.PERSON_ID!!, "123456789")
        mockMulighetsrommetApiServer.mockHentGjennomforingV2Data(
            gjennomforingId,
            MulighetsrommetGjennomforing(
                id = gjennomforingId,
                tiltakstype = MulighetsrommetGjennomforing.Tiltakstype(arenaKode = "ENKELAMO"),
                startDato = LocalDate.now(),
                sluttDato = LocalDate.now().plusDays(3),
                status = MulighetsrommetGjennomforing.Status.GJENNOMFORES,
                virksomhetsnummer = "999888777",
                oppstart = Oppstartstype.LOPENDE,
            ),
        )
        val json = objectMapper.writeValueAsString(
            KafkaMessageCreator.opprettArenaDeltaker(deltaker, opPos = position),
        )
        val tempCompleted = CountDownLatch(1)
        val originalCompleted = CountDownLatch(1)
        every {
            arenaConsumerServiceTemp.handleArenaGoldenGateRecord(
                match { it.key() == deltaker.TILTAKDELTAKER_ID.toString() && it.value() == json },
            )
        } answers {
            callOriginal()
            tempCompleted.countDown()
        }
        every {
            arenaMessageConsumerService.handleArenaGoldenGateRecord(
                match { it.key() == deltaker.TILTAKDELTAKER_ID.toString() && it.value() == json },
            )
        } answers {
            callOriginal()
            originalCompleted.countDown()
        }

        messageSender.publiserArenaDeltaker(deltaker.TILTAKDELTAKER_ID, json)

        await().untilAsserted {
            tempCompleted.count shouldBe 0L
            originalCompleted.count shouldBe 0L
            verify(atLeast = 1) {
                arenaConsumerServiceTemp.handleArenaGoldenGateRecord(
                    match {
                        it.topic() == kafkaTopicProperties.arenaTiltakDeltakerTopic &&
                            it.key() == deltaker.TILTAKDELTAKER_ID.toString() &&
                            it.value() == json
                    },
                )
            }
            val record = arenaDataRepository.get(
                ARENA_DELTAKER_TABLE_NAME,
                AmtOperation.CREATED,
                position,
            ).shouldNotBeNull()
            (record.ingestStatus in setOf(IngestStatus.NEW, IngestStatus.HANDLED)) shouldBe true
        }
    }

    @Test
    fun `produsenten publiserer tombstone med riktig topic og key gjennom ekte Kafka`() {
        val key = UUID.randomUUID()
        val properties = kafkaProperties.consumer().apply {
            setProperty(ConsumerConfig.GROUP_ID_CONFIG, "kafka-smoke-$key")
            setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }

        KafkaConsumer(properties, StringDeserializer(), StringDeserializer()).use { consumer ->
            consumer.subscribe(listOf(outputTopic))
            await().untilAsserted {
                consumer.poll(Duration.ofMillis(100))
                consumer.assignment().isNotEmpty() shouldBe true
            }

            producerService.tombstoneEnkeltplassDeltaker(key)

            var received: ConsumerRecord<String, String>? = null
            await().untilAsserted {
                if (received == null) {
                    received = consumer.poll(Duration.ofMillis(100)).firstOrNull { it.key() == key.toString() }
                }
                val record = received.shouldNotBeNull()
                record.topic() shouldBe outputTopic
                record.key() shouldBe key.toString()
                record.value() shouldBe null
            }
        }
    }
}
