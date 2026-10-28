package no.nav.amt.person.service.clients.krr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(KrrProxyClient::class)
class KrrProxyClientTest(
    private val sut: KrrProxyClient,
) : RestClientTestBase("digdir-krr-proxy") {
    @Test
    fun `hentKontaktinformasjon - enkelt personident - returnerer kontaktinformasjon`() {
        val personident = "12345678901"

        server
            .expect(requestTo("http://digdir-krr-proxy/rest/v1/personer?inkluderSikkerDigitalPost=false"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer digdir-krr-proxy-token"))
            .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
            .andExpect(jsonPath("$.personidenter[0]").value(personident))
            .andRespond(
                withSuccess(
                    """
                    {
                      "personer": {
                        "$personident": {
                          "personident": "$personident",
                          "epostadresse": "test@example.com",
                          "mobiltelefonnummer": "12345678"
                        }
                      },
                      "feil": {}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut.hentKontaktinformasjon(personident)

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe Kontaktinformasjon(epost = "test@example.com", telefonnummer = "12345678")
    }

    @Test
    fun `hentKontaktinformasjon - personident mangler i respons - failure`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{ "personer": {}, "feil": { "12345678901": "Person ikke funnet" } }""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut.hentKontaktinformasjon("12345678901")

        result.isFailure shouldBe true
    }

    @Test
    fun `hentKontaktinformasjon - 500 fra KRR - failure`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val result = sut.hentKontaktinformasjon(setOf("12345678901"))

        result.isFailure shouldBe true
        val exception = shouldThrow<RetryableUpstreamServiceException> {
            result.getOrThrow()
        }
        exception.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    @Test
    fun `hentKontaktinformasjon - tom HTTP 200-respons - failure med upstream-status`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val result = sut.hentKontaktinformasjon(setOf("12345678901"))

        val exception = shouldThrow<UpstreamServiceException> {
            result.getOrThrow()
        }
        exception.statusCode shouldBe HttpStatus.OK.value()
    }

    @Test
    fun `hentKontaktinformasjon - flere personidenter - sender alle og parser respons`() {
        val ident1 = "11111111111"
        val ident2 = "22222222222"

        server
            .expect(method(HttpMethod.POST))
            .andExpect(content().json("""{"personidenter":["$ident1","$ident2"]}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                      "personer": {
                        "$ident1": { "personident": "$ident1", "epostadresse": "a@a.no",  "mobiltelefonnummer": "1" },
                        "$ident2": { "personident": "$ident2", "epostadresse": null,      "mobiltelefonnummer": "2" }
                      },
                      "feil": {}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut.hentKontaktinformasjon(setOf(ident1, ident2)).getOrThrow()

        result shouldBe mapOf(
            ident1 to Kontaktinformasjon(epost = "a@a.no", telefonnummer = "1"),
            ident2 to Kontaktinformasjon(epost = null, telefonnummer = "2"),
        )
    }

    @Test
    fun `hentKontaktinformasjon - respons inneholder feil for noen personer - returnerer success med de som finnes`() {
        val ident1 = "11111111111"
        val ident2 = "22222222222"

        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "personer": {
                        "$ident1": { "personident": "$ident1", "epostadresse": "a@a.no", "mobiltelefonnummer": "1" }
                      },
                      "feil": { "$ident2": "Person ikke funnet" }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut
            .hentKontaktinformasjon(
                personidenter = setOf(ident1, ident2),
            ).shouldBeSuccess()

        result.size shouldBe 1
        result[ident1] shouldBe Kontaktinformasjon(epost = "a@a.no", telefonnummer = "1")
    }
}
