package no.nav.amt.arena.acl.mocks

import no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing
import no.nav.amt.arena.acl.clients.mulighetsrommet.toArenaKodeLocal
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import okhttp3.mockwebserver.MockResponse
import java.util.UUID

class MockMulighetsrommetApiServer : MockHttpServer() {
	fun mockHentGjennomforingId(
		arenaId: Long,
		gjennomforingId: UUID,
		responseCode: Int = 200,
	) {
		val body = """{"id": "$gjennomforingId"}"""

		val response = MockResponse().setResponseCode(responseCode).setBody(body)
		handleRequest(matchPath = "/api/v1/tiltaksgjennomforinger/id/$arenaId", response = response)
	}

	fun mockHentGjennomforingV2Data(
		id: UUID,
		gjennomforingData: Gjennomforing?,
	) {
		val response =
			if (gjennomforingData != null) {
				val tiltakskode = requireNotNull(
					Tiltakskode.entries.firstOrNull {
						it != Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER &&
							it.toArenaKodeLocal().name == gjennomforingData.tiltakstype.arenaKode
					},
				) { "Ingen tiltakskode for Arena-kode ${gjennomforingData.tiltakstype.arenaKode} i testdata" }
				val body =
					"""
					{
						"id": "$id",
						"tiltakskode": "${tiltakskode.name}",
						"arrangor": {
							"organisasjonsnummer": "${gjennomforingData.virksomhetsnummer}"
						},
						"oppstart": "${gjennomforingData.oppstart}"
					}
					""".trimIndent()
				MockResponse()
					.setResponseCode(200)
					.setBody(body)
			} else {
				MockResponse().setResponseCode(404).setBody("{}")
			}
		handleRequest(matchPath = "/api/v2/tiltaksgjennomforinger/$id", response = response)
	}
}
