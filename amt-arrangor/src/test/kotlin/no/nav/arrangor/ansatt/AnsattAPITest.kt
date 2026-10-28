package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.arrangor.ControllerTestBase
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.AnsattRolle.KOORDINATOR
import no.nav.arrangor.domain.AnsattRolle.VEILEDER
import no.nav.arrangor.domain.VeilederType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

class AnsattAPITest : ControllerTestBase() {
    @Nested
    @DisplayName("Tester at alle endepunkt er sikret")
    inner class EndpointsSecuredTests {
        @Test
        fun `getByPersonident - no token - unauthorized`() {
            sendRequest("GET", "/api/ansatt")
                .also { it.code shouldBe 401 }
        }

        @Test
        fun `getByPersonident - TokenX-token med feil audience - sanert challenge`() {
            val token = getTokenxToken(
                fnr = UUID.randomUUID().toString(),
                audience = "wrong-audience",
            )

            val response = sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer $token"),
            )

            response.code shouldBe 401
            response.header(HttpHeaders.WWW_AUTHENTICATE) shouldBe "Bearer"
        }

        @Test
        fun `getByPersonident - TokenX-token uten pid - unauthorized med samme feiltekst`() {
            val tokenUtenPid = getTokenxToken(
                fnr = "",
                claims = mapOf(
                    "acr" to "Level4",
                    "idp" to "idporten",
                    "client_id" to "amt-tiltaksarrangor-bff",
                ),
            )

            val response = sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer $tokenUtenPid"),
            )

            response.code shouldBe 401
            response.contentType shouldBe "application/problem+json"
            objectMapper.readTree(response.body.string())["detail"].asString() shouldBe "Ikke autentisert"
        }

        @Test
        fun `setKoordinatorForDeltakerliste - no token - unauthorized`() {
            sendRequest("POST", "/api/ansatt/koordinator/${UUID.randomUUID()}/${UUID.randomUUID()}", "")
                .also { it.code shouldBe 401 }
        }

        @Test
        fun `fjernKoordinatorForDeltakerliste - no token - unauthorized`() {
            sendRequest("DELETE", "/api/ansatt/koordinator/${UUID.randomUUID()}/${UUID.randomUUID()}")
                .also { it.code shouldBe 401 }
        }

        @Test
        fun `oppdaterVeiledereForDeltaker - no token - unauthorized`() {
            sendRequest(
                "POST",
                "/api/ansatt/veiledere/${UUID.randomUUID()}",
                objectMapper
                    .writeValueAsString(
                        AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                            arrangorId = UUID.randomUUID(),
                            veilederSomLeggesTil = listOf(AnsattAPI.VeilederAnsatt(UUID.randomUUID(), VeilederType.VEILEDER)),
                            veilederSomFjernes = emptyList(),
                        ),
                    ),
            ).also { it.code shouldBe 401 }
        }

        @Test
        fun `getByPersonident - token uten pid-claim - unauthorized`() {
            val token = getTokenxToken(
                fnr = UUID.randomUUID().toString(),
                claims = mapOf(
                    "acr" to "Level4",
                    "idp" to "idporten",
                    "client_id" to "amt-tiltaksarrangor-bff",
                ),
            )

            sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer $token"),
            ).also { it.code shouldBe 401 }
        }

        @Test
        fun `getByPersonident - Azure AD-token - unauthorized`() {
            sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
            ).also { it.code shouldBe 401 }
        }

        @Test
        fun `getByPersonident - TokenX-token med feil audience - unauthorized`() {
            sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(
                    HttpHeaders.AUTHORIZATION to
                        "Bearer ${getTokenxToken(UUID.randomUUID().toString(), audience = "wrong-audience")}",
                ),
            ).also { it.code shouldBe 401 }
        }

        @Test
        fun `getByPersonident - utløpt TokenX-token - unauthorized`() {
            sendRequest(
                method = "GET",
                path = "/api/ansatt",
                headers = mapOf(
                    HttpHeaders.AUTHORIZATION to
                        "Bearer ${getTokenxToken(UUID.randomUUID().toString(), expiry = -1)}",
                ),
            ).also { it.code shouldBe 401 }
        }
    }

    @Nested
    @DisplayName("/api/ansatt/ tester")
    inner class GetByPersonidentTests {
        @Test
        fun `getAnsattByPersonident - returnerer Ansatt`() {
            val arrangorOne = testDatabase.insertArrangor()
            val arrangorTwo = testDatabase.insertArrangor()
            val personident = UUID.randomUUID().toString()
            val personId = UUID.randomUUID()

            mockAltinnRoller(
                personident,
                mapOf(
                    arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
                    arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
                ),
            )

            mockPerson(personident, personId, "Test", null, "Testersen")

            val ansatt = getAnsatt(personident)

            ansatt.personalia.personident shouldBe personident
            ansatt.arrangorer.size shouldBe 2

            getRoller(ansatt, arrangorOne.id) shouldContainExactly listOf(KOORDINATOR)
            getRoller(ansatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
        }

        @Test
        fun `getAnsattByPersonident - om lastSynchronized over 1 time - oppdater ansattroller`() {
            val arrangorOne = testDatabase.insertArrangor()
            val arrangorTwo = testDatabase.insertArrangor()
            val arrangorThree = testDatabase.insertArrangor()
            val personident = UUID.randomUUID().toString()
            val personId = UUID.randomUUID()

            mockAltinnRoller(
                personident,
                mapOf(
                    arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
                    arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
                ),
            )

            mockPerson(personident, personId, "Test", null, "Testersen")
            val oldAnsatt = getAnsatt(personident)

            testDatabase.setLastSynchronized(oldAnsatt.id, LocalDateTime.now().minusMinutes(61))

            resetClientMocks()
            mockAltinnRoller(
                personident,
                mapOf(
                    arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR),
                    arrangorThree.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
                ),
            )

            val nyAnsatt = getAnsatt(personident)

            nyAnsatt.personalia.navn.fornavn shouldBe "Test"

            nyAnsatt.arrangorer.map { it.arrangorId } shouldContainExactlyInAnyOrder listOf(arrangorTwo.id, arrangorThree.id)

            getRoller(nyAnsatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR)
            getRoller(nyAnsatt, arrangorThree.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
        }
    }

    private fun getRoller(
        ansatt: Ansatt,
        arrangorId: UUID,
    ): List<AnsattRolle> = ansatt.arrangorer.first { it.arrangorId == arrangorId }.roller

    private fun getAnsatt(personident: String): Ansatt = sendRequest(
        method = "GET",
        path = "/api/ansatt",
        headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = personident)}"),
    ).also { it.code shouldBe 200 }
        .body
        .string()
        .let { objectMapper.readValue(it) }
}
