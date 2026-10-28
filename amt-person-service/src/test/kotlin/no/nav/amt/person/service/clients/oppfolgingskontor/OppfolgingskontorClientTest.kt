package no.nav.amt.person.service.clients.oppfolgingskontor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import org.hamcrest.CoreMatchers.containsString
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

@RestClientTest(OppfolgingskontorClient::class)
class OppfolgingskontorClientTest(
    private val sut: OppfolgingskontorClient,
) : RestClientTestBase("ao-oppfolgingskontor") {
    @Test
    fun `hentKontorForBruker skal lage riktig request og parse respons`() {
        server
            .expect(requestTo("http://ao-oppfolgingskontor/graphql"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ao-oppfolgingskontor-token"))
            .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
            .andExpect(content().string(containsString("kontorTilhorigheter")))
            .andExpect(content().string(containsString("12345678901")))
            .andRespond(
                withSuccess(
                    """
                    {
                        "data": {
                            "kontorTilhorigheter": {
                                "arbeidsoppfolging": {
                                    "kontorId": "1234",
                                    "kontorNavn": "NAV Testkontor"
                                }
                            }
                        }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val kontor = sut.hentKontorForBruker("12345678901").shouldNotBeNull()

        kontor.kontorId shouldBe "1234"
        kontor.kontorNavn shouldBe "NAV Testkontor"
    }

    @Test
    fun `hentKontorForBruker skal returnere null hvis arbeidsoppfolging er null i respons`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                        "data": {
                            "kontorTilhorigheter": {
                                "arbeidsoppfolging": null
                            }
                        }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentKontorForBruker("12345678901") shouldBe null
    }

    @Test
    fun `hentKontorForBruker skal kaste exception ved GraphQL feil`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                        "errors": [
                            {"message": "Bruker ikke funnet"}
                        ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val thrown = shouldThrow<RuntimeException> {
            sut.hentKontorForBruker("12345678901")
        }

        thrown.message.shouldStartWith("Feilmeldinger i respons fra ao-oppfolgingskontor")
    }

    @Test
    fun `hentKontorForBruker skal klassifisere HTTP 500 som retrybar`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val thrown = shouldThrow<RetryableUpstreamServiceException> {
            sut.hentKontorForBruker("12345678901")
        }

        thrown.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    @Test
    fun `hentKontorForBruker skal kaste UpstreamServiceException ved tom HTTP 200-respons`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val thrown = shouldThrow<UpstreamServiceException> {
            sut.hentKontorForBruker("12345678901")
        }

        thrown.statusCode shouldBe HttpStatus.OK.value()
    }

    @Test
    fun `hentKontorForBruker skal kaste exception når data er null uten errors`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{ "data": null }""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val thrown = shouldThrow<RuntimeException> {
            sut.hentKontorForBruker("12345678901")
        }

        thrown.message shouldBe "ao-oppfolgingskontor respons inneholder ikke data"
    }
}
