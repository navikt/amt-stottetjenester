package no.nav.amt.tiltak.data_publisher.publish

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.VurderingFraArrangorData
import no.nav.amt.tiltak.common.json.JsonUtils
import no.nav.amt.tiltak.core.port.UnleashService
import no.nav.amt.tiltak.data_publisher.DatabaseTestDataHandler
import no.nav.amt.tiltak.test.database.DbTestDataUtils
import no.nav.amt.tiltak.test.database.SingletonPostgresContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerPublishQueryTest : FunSpec({
	val dataSource = SingletonPostgresContainer.getDataSource()
	val template = NamedParameterJdbcTemplate(dataSource)
	val unleashService = mockk<UnleashService>()

	val query = DeltakerPublishQuery(template, unleashService)
	val db = DatabaseTestDataHandler(template)

	beforeEach {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	afterEach {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	test("get") {
		every { unleashService.erKometMasterForTiltakstype(any()) } returns false
		val input = db.createDeltaker()

		when (val data = query.get(input.id, null)) {
			is DeltakerPublishQuery.Result.OK -> data.result.id shouldBe input.id
			else -> fail("Should be ok, was $data")
		}
	}

	test("get - deltaker som komet er master for, uten vurdering, skal ikke publisere") {
		every { unleashService.erKometMasterForTiltakstype(any()) } returns true
		val input = db.createDeltaker()

		when (val data = query.get(input.id, null)) {
			is DeltakerPublishQuery.Result.DontPublish -> {}
			else -> fail("Should be ok, was $data")
		}
	}

	test("get - deltaker som arena er master for skal opprettes med importertFraArena data") {
		every { unleashService.erKometMasterForTiltakstype(any()) } returns false
		val deltakerInput = db.createDeltaker()

		when (val data = query.get(deltakerInput.id, null)) {
			is DeltakerPublishQuery.Result.OK -> {
				data.result.historikk!!.size shouldBe 1
				val importertFraArenaElement = (data.result.historikk[0] as DeltakerHistorikk.ImportertFraArena).importertFraArena
				importertFraArenaElement.deltakerId shouldBe deltakerInput.id
				importertFraArenaElement.importertDato.toLocalDate() shouldBe LocalDate.now()
				importertFraArenaElement.deltakerVedImport.deltakerId shouldBe deltakerInput.id
				importertFraArenaElement.deltakerVedImport.startdato shouldBe deltakerInput.startDato
				importertFraArenaElement.deltakerVedImport.sluttdato shouldBe deltakerInput.sluttDato

			}
			else -> fail("Should be ok, was $data")
		}
	}
	test("get - deltaker som arena er master for skal opprettes med vurderinger data") {
		every { unleashService.erKometMasterForTiltakstype(any()) } returns false
		val deltakerInput = db.createDeltaker()
		val arrangorAnsatt = db.createArrangorAnsatt()
		val vurdering = db.createVurdering(deltakerId = deltakerInput.id, opprettetAvArrangorAnsattId = arrangorAnsatt.id)


		when (val data = query.get(deltakerInput.id, null)) {
			is DeltakerPublishQuery.Result.OK -> {
				data.result.historikk!!.size shouldBe 2
				val importertFraArenaElement = (data.result.historikk.first { it is DeltakerHistorikk.ImportertFraArena } as DeltakerHistorikk.ImportertFraArena).importertFraArena
				importertFraArenaElement.deltakerId shouldBe deltakerInput.id
				importertFraArenaElement.importertDato.toLocalDate() shouldBe LocalDate.now()
				importertFraArenaElement.deltakerVedImport.deltakerId shouldBe deltakerInput.id
				importertFraArenaElement.deltakerVedImport.startdato shouldBe deltakerInput.startDato
				importertFraArenaElement.deltakerVedImport.sluttdato shouldBe deltakerInput.sluttDato

				val vurderingElement = (data.result.historikk.first { it is DeltakerHistorikk.VurderingFraArrangor } as DeltakerHistorikk.VurderingFraArrangor).data
				vurderingElement.deltakerId shouldBe deltakerInput.id
				vurderingElement.begrunnelse shouldBe vurdering.begrunnelse
				vurderingElement.vurderingstype shouldBe Vurderingstype.valueOf(vurdering.vurderingstype.name)

			}
			else -> fail("Should be ok, was $data")
		}
	}
})

class DeltakerHistorikkSerializationTest {
	@Test
	fun `serializes Arena import history`() {
		val deltakerId = UUID.fromString("11111111-1111-4111-8111-111111111111")
		val historikk = DeltakerHistorikk.ImportertFraArena(
			importertFraArena = ImportertFraArena(
				deltakerId = deltakerId,
				importertDato = LocalDateTime.parse("2026-04-02T03:04:05"),
				deltakerVedImport = DeltakerVedImport(
					deltakerId = deltakerId,
					innsoktDato = LocalDate.parse("2026-03-01"),
					startdato = LocalDate.parse("2026-03-08"),
					sluttdato = LocalDate.parse("2026-10-12"),
					dagerPerUke = 4.5f,
					deltakelsesprosent = 87.5f,
					status = DeltakerStatus(
						id = UUID.fromString("22222222-2222-4222-8222-222222222222"),
						type = DeltakerStatus.Type.DELTAR,
						aarsak = DeltakerStatus.Aarsak(
							type = DeltakerStatus.Aarsak.Type.ANNET,
							beskrivelse = "synthetic reason",
						),
						gyldigFra = LocalDateTime.parse("2026-03-01T10:15:30"),
						gyldigTil = null,
						opprettet = LocalDateTime.parse("2026-03-01T10:14:00"),
					),
				),
			),
		)

		val actual = JsonUtils.objectMapper.readTree(JsonUtils.objectMapper.writeValueAsString(historikk))
		val expected = JsonUtils.objectMapper.readTree(
			"""
			{
			  "type": "ImportertFraArena",
			  "importertFraArena": {
			    "deltakerId": "11111111-1111-4111-8111-111111111111",
			    "importertDato": "2026-04-02T03:04:05",
			    "deltakerVedImport": {
			      "deltakerId": "11111111-1111-4111-8111-111111111111",
			      "innsoktDato": "2026-03-01",
			      "startdato": "2026-03-08",
			      "sluttdato": "2026-10-12",
			      "dagerPerUke": 4.5,
			      "deltakelsesprosent": 87.5,
			      "status": {
			        "id": "22222222-2222-4222-8222-222222222222",
			        "type": "DELTAR",
			        "aarsak": {
			          "type": "ANNET",
			          "beskrivelse": "synthetic reason"
			        },
			        "gyldigFra": "2026-03-01T10:15:30",
			        "gyldigTil": null,
			        "opprettet": "2026-03-01T10:14:00"
			      }
			    }
			  },
			  "sistEndret": "2026-04-02T03:04:05",
			  "sorteringsDato": "2026-03-01T00:00:00"
			}
			""".trimIndent(),
		)

		assertEquals(expected, actual)
	}

	@Test
	fun `serializes organizer evaluation history`() {
		val historikk = DeltakerHistorikk.VurderingFraArrangor(
			data = VurderingFraArrangorData(
				id = UUID.fromString("33333333-3333-4333-8333-333333333333"),
				deltakerId = UUID.fromString("11111111-1111-4111-8111-111111111111"),
				vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
				begrunnelse = "synthetic evaluation",
				opprettetAvArrangorAnsattId = UUID.fromString("44444444-4444-4444-8444-444444444444"),
				opprettet = LocalDateTime.parse("2026-05-06T07:08:09"),
			),
		)

		val actual = JsonUtils.objectMapper.readTree(JsonUtils.objectMapper.writeValueAsString(historikk))
		val expected = JsonUtils.objectMapper.readTree(
			"""
			{
			  "type": "VurderingFraArrangor",
			  "data": {
			    "id": "33333333-3333-4333-8333-333333333333",
			    "deltakerId": "11111111-1111-4111-8111-111111111111",
			    "vurderingstype": "OPPFYLLER_KRAVENE",
			    "begrunnelse": "synthetic evaluation",
			    "opprettetAvArrangorAnsattId": "44444444-4444-4444-8444-444444444444",
			    "opprettet": "2026-05-06T07:08:09"
			  },
			  "sistEndret": "2026-05-06T07:08:09",
			  "sorteringsDato": "2026-05-06T07:08:09"
			}
			""".trimIndent(),
		)

		assertEquals(expected, actual)
	}
}
