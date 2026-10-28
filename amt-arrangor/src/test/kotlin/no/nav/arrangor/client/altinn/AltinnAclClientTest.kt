package no.nav.arrangor.client.altinn

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arrangor.client.RestClientTestBase
import no.nav.arrangor.domain.AnsattRolle
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(AltinnAclClient::class)
class AltinnAclClientTest(
    private val sut: AltinnAclClient,
) : RestClientTestBase("amt-altinn") {
    @Test
    fun `hentRoller - sender riktig request og mapper respons`() {
        val bearerToken = "Bearer amt-altinn-token"
        server
            .expect(requestTo("http://amt-altinn/api/v1/rolle/tiltaksarrangor"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(content().json("""{"personident":"12345678910"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                      "roller": [
                        {
                          "organisasjonsnummer": "123456789",
                          "roller": ["KOORDINATOR", "VEILEDER"]
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentRoller("12345678910").getOrThrow() shouldBe
            listOf(AltinnRolle("123456789", listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)))
    }

    @Test
    fun `hentRoller - feilrespons returnerer failure`() {
        server
            .expect(requestTo("http://amt-altinn/api/v1/rolle/tiltaksarrangor"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        sut.hentRoller("12345678910").isFailure shouldBe true
    }

    @Test
    fun `hentRoller - 404 returnerer failure med NoSuchElementException`() {
        server
            .expect(requestTo("http://amt-altinn/api/v1/rolle/tiltaksarrangor"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        sut.hentRoller("12345678910").exceptionOrNull().shouldBeInstanceOf<NoSuchElementException>()
    }

    @Test
    fun `hentRoller - ukjent rolle returnerer failure med UkjentAltinnRolleException`() {
        server
            .expect(requestTo("http://amt-altinn/api/v1/rolle/tiltaksarrangor"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "roller": [
                        {
                          "organisasjonsnummer": "123456789",
                          "roller": ["UKJENT_ROLLE"]
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val exception = sut
            .hentRoller("12345678910")
            .exceptionOrNull()
            .shouldBeInstanceOf<UkjentAltinnRolleException>()

        exception.message shouldBe "Ukjent tiltaksarrangørrolle"
        exception.message shouldNotContain "UKJENT_ROLLE"
        exception.rolleForLogging shouldBe "UKJENT_ROLLE"
        UkjentAltinnRolleException("ukjent-rolle").rolleForLogging shouldBe "uventet format"
    }
}
