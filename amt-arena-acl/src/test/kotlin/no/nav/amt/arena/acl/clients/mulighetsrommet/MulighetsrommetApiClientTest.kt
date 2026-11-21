package no.nav.amt.arena.acl.clients.mulighetsrommet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.amt.arena.acl.mocks.MockMulighetsrommetApiServer
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import okhttp3.mockwebserver.MockResponse
import org.springframework.http.HttpHeaders
import java.time.LocalDate
import java.util.UUID

class MulighetsrommetApiClientTest :
	FunSpec({

		val server = MockMulighetsrommetApiServer().apply { start() }

		val client =
			MulighetsrommetApiClient(
				baseUrl = server.serverUrl(),
				tokenProvider = { "TOKEN" },
			)

		afterEach {
			server.reset()
		}

		listOf("INDOPPFAG", "ENKELAMO", "ENKFAGYRKE", "HOYEREUTD").forEach { arenaKode ->
			test("hentGjennomforingV2 - mockserveren beholder tiltakstype $arenaKode") {
				val id = UUID.randomUUID()
				server.mockHentGjennomforingV2Data(
					id,
					Gjennomforing(
						id = id,
						tiltakstype = Gjennomforing.Tiltakstype(arenaKode = arenaKode),
						virksomhetsnummer = "999888777",
						oppstart = Oppstartstype.LOPENDE,
					),
				)

				val gjennomforing = client.hentGjennomforingV2(id)

				gjennomforing.tiltakstype.arenaKode shouldBe arenaKode
				gjennomforing.erEnkelplass() shouldBe (arenaKode != "INDOPPFAG")
				server.latestRequest().path shouldBe "/api/v2/tiltaksgjennomforinger/$id"
			}
		}

		test("hentGjennomforingId - skal lage riktig request og parse respons") {
			val id = UUID.randomUUID()
			val arenaId = "1234"

			server.handleRequest(
				response =
					MockResponse().setBody(
						"""
						{
							"id": "$id"
						}
						""".trimIndent(),
					),
			)
			val returnedId = client.hentGjennomforingId(arenaId)
			val request = server.latestRequest()

			returnedId shouldBe id

			request.path shouldBe "/api/v1/tiltaksgjennomforinger/id/$arenaId"
			request.method shouldBe "GET"
			request.getHeader(HttpHeaders.AUTHORIZATION) shouldBe "Bearer TOKEN"
		}

		test("hentGjennomforingId - skal returnere null hvis status er 404") {
			server.handleRequest(response = MockResponse().setResponseCode(404))

			val returnedId = client.hentGjennomforingId("1234")
			returnedId shouldBe null
		}

		test("hentGjennomforing - skal lage riktig request og parse respons") {
			val id = UUID.randomUUID()
			val startDato = LocalDate.now()
			val virksomhetsnummer = "8530254"
			server.handleRequest(
				response =
					MockResponse().setBody(
						"""
						{
							"id": "$id",
							"tiltakstype": {
								"id": "${UUID.randomUUID()}",
								"navn": "tiltaksnavn",
								"arenaKode": "INDOPPFAG"
							},
							"navn": "navn på gjennomføring",
							"status": "GJENNOMFORES",
							"startDato": "$startDato",
							"sluttDato": null,
							"virksomhetsnummer": "$virksomhetsnummer",
							"oppstart": "LOPENDE"
						}
						""".trimIndent(),
					),
			)

			val gjennomforing = client.hentGjennomforing(id)

			val request = server.latestRequest()

			gjennomforing.virksomhetsnummer shouldBe virksomhetsnummer
			gjennomforing.status shouldBe Gjennomforing.Status.GJENNOMFORES
			gjennomforing.startDato shouldBe startDato
			gjennomforing.erKurs() shouldBe false

			request.path shouldBe "/api/v1/tiltaksgjennomforinger/$id"
			request.method shouldBe "GET"
			request.getHeader(HttpHeaders.AUTHORIZATION) shouldBe "Bearer TOKEN"
		}
	})
