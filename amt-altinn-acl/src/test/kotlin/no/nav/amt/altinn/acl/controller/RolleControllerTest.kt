package no.nav.amt.altinn.acl.controller

import io.mockk.every
import io.mockk.verify
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.testutil.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class RolleControllerTest(
    private val mockMvc: MockMvc,
) : IntegrationTest() {
    @Test
    fun `hentTiltaksarrangorRoller - should return 401 when not authenticated`() {
        mockMvc
            .post(PATH) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "12345678910"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `hentTiltaksarrangorRoller - should return 403 when not machine-to-machine request`() {
        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "12345678910"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `hentTiltaksarrangorRoller - token med feil audience - returnerer 401`() {
        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdToken(audience = "feil-audience")) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "12345678910"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `hentTiltaksarrangorRoller - feil i Altinn for ny person gir tom liste`() {
        val norskIdent = "12345678910"
        every { altinnClient.hentRoller(norskIdent, RolleType.entries) } throws
            IllegalStateException("Intern feildetalj fra Altinn")

        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "$norskIdent"}"""
            }.andExpect {
                status { isOk() }
                content { json("""{"roller":[]}""", JsonCompareMode.STRICT) }
            }

        verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
    }

    @Test
    fun `hentTiltaksarrangorRoller - ugyldig JSON gir fortsatt 400`() {
        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpect {
                status { isBadRequest() }
                content { contentType("application/problem+json") }
                content {
                    json(
                        """
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Forespørselen inneholder ugyldige data",
                          "instance": "$PATH"
                        }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `hentTiltaksarrangorRoller - ugyldig HTTP-metode gir sanitert 405`() {
        mockMvc
            .get(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
            }.andExpect {
                status { isMethodNotAllowed() }
                content { contentType("application/problem+json") }
                content {
                    json(
                        """
                        {
                          "title": "Method Not Allowed",
                          "status": 405,
                          "detail": "Forespørselen kunne ikke behandles",
                          "instance": "$PATH"
                        }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @ParameterizedTest(name = "personident = \"{0}\" gir 400")
    @ValueSource(strings = ["1234567891K", "1234567891", "123456789101", "", "           "])
    fun `hentTiltaksarrangorRoller - returnerer 400 hvis personident har feil format`(personident: String) {
        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "$personident"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType("application/problem+json") }
                content {
                    json(
                        """
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Forespørselen inneholder ugyldige data",
                          "instance": "$PATH"
                        }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }

        verify(exactly = 0) { altinnClient.hentRoller(any(), any()) }
    }

    @Test
    fun `hentTiltaksarrangorRoller - person uten roller - returnerer tom liste`() {
        val norskIdent = "12345678910"

        mockAltinnRoller(norskIdent, emptyList(), emptyList())

        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "$norskIdent"}"""
            }.andExpect {
                status { isOk() }
                content { json("""{"roller":[]}""", JsonCompareMode.STRICT) }
            }
    }

    @Test
    fun `hentTiltaksarrangorRoller - trimmer personident før bruk`() {
        val norskIdent = "12345678910"
        mockAltinnRoller(norskIdent, emptyList(), emptyList())

        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": " $norskIdent "}"""
            }.andExpect {
                status { isOk() }
                content { json("""{"roller":[]}""", JsonCompareMode.STRICT) }
            }

        verify(exactly = 1) { altinnClient.hentRoller(norskIdent, RolleType.entries) }
    }

    @Test
    fun `hentTiltaksarrangorRoller - should return 200 with correct response`() {
        val norskIdent = "12345678910"
        val orgnr = "1234567"

        mockAltinnRoller(norskIdent, listOf(RolleType.KOORDINATOR, RolleType.VEILEDER), listOf(orgnr))

        mockMvc
            .post(PATH) {
                headers { setBearerAuth(issueAzureAdM2MToken()) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"personident": "$norskIdent"}"""
            }.andExpect {
                status { isOk() }
                content {
                    json(
                        """{"roller":[{"organisasjonsnummer":"$orgnr","roller":["KOORDINATOR","VEILEDER"]}]}""",
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `hentTiltaksarrangorRoller - returnerer lagrede roller uten nytt Altinn-kall ved gjentatt forespørsel`() {
        val personIdent = "12345678910"
        val orgnr = "1234567"
        val forventetJson = """{"roller":[{"organisasjonsnummer":"$orgnr","roller":["KOORDINATOR"]}]}"""

        mockAltinnRoller(personIdent, listOf(RolleType.KOORDINATOR), listOf(orgnr))

        repeat(2) {
            mockMvc
                .post(PATH) {
                    headers { setBearerAuth(issueAzureAdM2MToken()) }
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": "$personIdent"}"""
                }.andExpect {
                    status { isOk() }
                    content { json(forventetJson, JsonCompareMode.STRICT) }
                }
        }

        verify(exactly = 1) { altinnClient.hentRoller(personIdent, RolleType.entries) }
    }

    companion object {
        private const val PATH = "/api/v1/rolle/tiltaksarrangor"
    }
}
