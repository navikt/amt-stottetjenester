package no.nav.amt.arena.acl.integration

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.arena.acl.domain.Gjennomforing
import no.nav.amt.arena.acl.domain.db.IngestStatus
import no.nav.amt.arena.acl.domain.kafka.amt.AmtDeltaker
import no.nav.amt.arena.acl.domain.kafka.amt.AmtKafkaMessageDto
import no.nav.amt.arena.acl.domain.kafka.amt.AmtOperation
import no.nav.amt.arena.acl.domain.kafka.amt.PayloadType
import no.nav.amt.arena.acl.domain.kafka.arena.ArenaGjennomforing
import no.nav.amt.arena.acl.domain.kafka.arena.ArenaHistDeltaker
import no.nav.amt.arena.acl.domain.kafka.arena.TiltakDeltaker
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageConsumer
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageCreator
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageSender
import no.nav.amt.arena.acl.repositories.ArenaDataRepository
import no.nav.amt.arena.acl.services.GjennomforingService
import no.nav.amt.arena.acl.services.SUPPORTED_TILTAK
import no.nav.amt.arena.acl.utils.ARENA_HIST_DELTAKER_TABLE_NAME
import no.nav.amt.arena.acl.utils.JsonUtils.objectMapper
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HistDeltakerIntegrationTest(
	private val kafkaMessageSender: KafkaMessageSender,
	private val arenaDataRepository: ArenaDataRepository,
	private val gjennomforingService: GjennomforingService,
) : IntegrationTestBase() {
	val baseDeltaker = createDeltaker()

	val baseGjennomforing =
		KafkaMessageCreator.baseGjennomforing(
			arenaGjennomforingId = baseDeltaker.TILTAKGJENNOMFORING_ID,
			arbgivIdArrangor = 68968L,
			datoFra = LocalDateTime.now().minusDays(3),
			datoTil = LocalDateTime.now().plusDays(3),
		)

	val gjennomforingIdMR: UUID = UUID.randomUUID()
	val gjennomforingMRData =
		no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing(
			id = gjennomforingIdMR,
			tiltakstype =
				no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing.Tiltakstype(
					id = UUID.randomUUID(),
					navn = "Navn på tiltak",
					arenaKode = "INDOPPFAG",
				),
			navn = "Navn på gjennomføring",
			startDato = LocalDate.now(),
			sluttDato = LocalDate.now().plusDays(3),
			status = no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing.Status.GJENNOMFORES,
			virksomhetsnummer = "999888777",
			oppstart = Oppstartstype.LOPENDE,
		)

	val fnr = "123456789"

	@BeforeEach
	fun setupMocks() {
		mockMulighetsrommetApiServer.reset()
		mockArenaOrdsProxyHttpServer.reset()
		mockMulighetsrommetApiServer.mockHentGjennomforingId(
			baseGjennomforing.TILTAKGJENNOMFORING_ID,
			gjennomforingIdMR,
		)
		mockMulighetsrommetApiServer.mockHentGjennomforingV2Data(gjennomforingIdMR, gjennomforingMRData)
	}

	@Test
	fun `ingest deltaker - har deltakelse på samme gjennomforing, andre datoer - status HANDLED`() {
		mockArenaOrdsProxyHttpServer.mockHentFnr(baseDeltaker.PERSON_ID!!, fnr)

		val pos = "52"
		gjennomforingService.upsert(baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(), SUPPORTED_TILTAK.first(), true)

		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.HIST_TILTAKDELTAKER_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(baseDeltaker, opPos = pos)),
		)

		await().untilAsserted {
			val deltakerRecord = kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_TILTAK)
			deltakerRecord.shouldNotBeNull()

			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.CREATED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.HANDLED
		}
	}

	@Test
	fun `ingest deltaker - slettemelding - deltaker blir ikke slettet, melding blir ignored`() {
		mockArenaOrdsProxyHttpServer.mockHentFnr(baseDeltaker.PERSON_ID!!, fnr)

		gjennomforingService.upsert(baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(), SUPPORTED_TILTAK.first(), true)

		val pos = "42"
		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.TILTAKGJENNOMFORING_ID,
			objectMapper.writeValueAsString(
				KafkaMessageCreator.opprettArenaHistDeltaker(
					arenaDeltaker = baseDeltaker,
					opPos = pos,
					opType = "D",
				),
			),
		)

		await().untilAsserted {
			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.DELETED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.IGNORED

			val deltakerRecord = kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_TILTAK)
			deltakerRecord.shouldBeNull()
		}
	}

	@Test
	fun `ingest deltaker - ikke stottet tiltak - skal ignoreres`() {
		mockArenaOrdsProxyHttpServer.mockHentFnr(baseDeltaker.PERSON_ID!!, fnr)

		gjennomforingService.upsert(baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(), "IKKESTOTTET", true)

		val pos = "42"
		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.TILTAKGJENNOMFORING_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(arenaDeltaker = baseDeltaker, opPos = pos)),
		)

		await().untilAsserted {
			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.CREATED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.IGNORED
		}
	}

	@Test
	fun `ingest deltaker - gjennomforing er ugyldig - deltaker far status WAITING`() {
		mockArenaOrdsProxyHttpServer.mockHentFnr(baseDeltaker.PERSON_ID!!, fnr)
		gjennomforingService.upsert(
			baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(),
			SUPPORTED_TILTAK.first(),
			false,
		)

		val pos = "77"
		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.HIST_TILTAKDELTAKER_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(arenaDeltaker = baseDeltaker, opPos = pos)),
		)

		await().untilAsserted {
			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.CREATED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.WAITING
		}
	}

	@Test
	fun `ingest deltaker - arena mangler fnr for personId - status INVALID`() {
		val pos = "33"
		mockArenaOrdsProxyHttpServer.mockFailHentFnr(baseDeltaker.PERSON_ID!!)

		gjennomforingService.upsert(baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(), SUPPORTED_TILTAK.first(), true)
		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.HIST_TILTAKDELTAKER_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(baseDeltaker, opPos = pos)),
		)

		await().untilAsserted {
			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.CREATED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.RETRY

			val deltakerRecord = kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_TILTAK)
			deltakerRecord.shouldBeNull()
		}
	}

	@Test
	fun `ingest deltaker - enkeltplass - ignoreres og publiseres ikke`() {
		mockMulighetsrommetApiServer.reset()
		mockMulighetsrommetApiServer.mockHentGjennomforingId(baseGjennomforing.TILTAKGJENNOMFORING_ID, gjennomforingIdMR)
		mockMulighetsrommetApiServer.mockHentGjennomforingV2Data(
			gjennomforingIdMR,
			gjennomforingMRData,
			Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
		)
		mockArenaOrdsProxyHttpServer.mockHentFnr(baseDeltaker.PERSON_ID!!, fnr)
		gjennomforingService.upsert(baseGjennomforing.TILTAKGJENNOMFORING_ID.toString(), "ENKELAMO", true)

		val pos = "88"
		kafkaMessageSender.publiserArenaHistDeltaker(
			baseDeltaker.HIST_TILTAKDELTAKER_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(baseDeltaker, opPos = pos)),
		)

		await().untilAsserted {
			val arenaData =
				arenaDataRepository.get(
					tableName = ARENA_HIST_DELTAKER_TABLE_NAME,
					operation = AmtOperation.CREATED,
					position = pos,
				)
			arenaData.shouldNotBeNull()
			arenaData.ingestStatus shouldBe IngestStatus.IGNORED
		}

		kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_TILTAK).shouldBeNull()
		kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_ENKELTPLASS_DELTAKER).shouldBeNull()
	}

	private fun AmtDeltaker.validate(baseDeltaker: ArenaHistDeltaker) {
		dagerPerUke shouldBe baseDeltaker.ANTALL_DAGER_PR_UKE
		innsokBegrunnelse shouldBe baseDeltaker.BEGRUNNELSE_BESTILLING
		prosentDeltid shouldBe baseDeltaker.PROSENT_DELTID
		sluttDato shouldBe parseDate(baseDeltaker.DATO_TIL)
		startDato shouldBe parseDate(baseDeltaker.DATO_FRA)
		registrertDato shouldBe parseDateTime(baseDeltaker.REG_DATO)
	}

	private fun createDeltaker() =
		KafkaMessageCreator.baseHistDeltaker(
			deltakerStatusKode = TiltakDeltaker.Status.FULLF.name,
			startDato = LocalDate.now().minusMonths(3),
			sluttDato = LocalDate.now().minusMonths(2),
		)

	private fun ArenaGjennomforing.publiser(customAssertions: (payload: Gjennomforing?) -> Unit) {
		kafkaMessageSender.publiserArenaGjennomforing(
			TILTAKGJENNOMFORING_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaGjennomforingMessage(this)),
		)

		await().untilAsserted {
			val gjennomforingResult = gjennomforingService.get(TILTAKGJENNOMFORING_ID.toString())
			customAssertions(gjennomforingResult)
		}
	}

	private fun ArenaHistDeltaker.publiserOgValiderOutput(customAssertions: (payload: AmtDeltaker) -> Unit) {
		kafkaMessageSender.publiserArenaHistDeltaker(
			HIST_TILTAKDELTAKER_ID,
			objectMapper.writeValueAsString(KafkaMessageCreator.opprettArenaHistDeltaker(this)),
		)

		await().untilAsserted {
			val deltakerRecord = kafkaMessageConsumer.getLatestRecord(KafkaMessageConsumer.Topic.AMT_TILTAK)
			deltakerRecord.shouldNotBeNull()

			val deltakerResult = objectMapper.readValue<AmtKafkaMessageDto<AmtDeltaker>>(deltakerRecord.value())
			assertSoftly(deltakerResult) {
				type shouldBe PayloadType.DELTAKER
				operation shouldBe AmtOperation.MODIFIED

				assertSoftly(payload.shouldNotBeNull()) {
					personIdent shouldBe fnr
					gjennomforingId shouldBe gjennomforingIdMR
					customAssertions(it)
					validate(this@publiserOgValiderOutput)
				}
			}
		}
	}

	private fun parseDate(dateStr: String?): LocalDate? {
		val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		return dateStr?.let { LocalDate.parse(dateStr, dateFormatter) }
	}

	private fun parseDateTime(dateStr: String?): LocalDateTime? {
		val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		return dateStr?.let { LocalDateTime.parse(dateStr, dateFormatter) }
	}
}
