package no.nav.arrangor.client.enhetsregister

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arrangor.client.RestClientTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(EnhetsregisterClient::class)
class EnhetsregisterClientTest(
    private val sut: EnhetsregisterClient,
) : RestClientTestBase("amt-enhetsregister") {
    @Nested
    inner class HentVirksomhet {
        @Test
        fun `sender riktig request og parser respons`() {
            server
                .expect(requestTo("http://amt-enhetsregister/api/enhet/123456789"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-enhetsregister-token"))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "organisasjonsnummer": "123456789",
                          "navn": "Test AS",
                          "overordnetEnhetOrganisasjonsnummer": "987654321",
                          "overordnetEnhetNavn": "Overordnet AS"
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            sut.hentVirksomhet("123456789").getOrThrow() shouldBe
                Virksomhet("123456789", "Test AS", "987654321", "Overordnet AS")
        }

        @Test
        fun `ugyldig organisasjonsnummer kaller ikke tjenesten`() {
            sut.hentVirksomhet("123").isFailure shouldBe true
        }

        @Test
        fun `feilrespons returnerer failure`() {
            server
                .expect(requestTo("http://amt-enhetsregister/api/enhet/123456789"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            sut.hentVirksomhet("123456789").isFailure shouldBe true
        }

        @Test
        fun `404 returnerer failure med NoSuchElementException`() {
            server
                .expect(requestTo("http://amt-enhetsregister/api/enhet/123456789"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            sut.hentVirksomhet("123456789").exceptionOrNull().shouldBeInstanceOf<NoSuchElementException>()
        }
    }

    @Nested
    inner class ValidateBaseUrl {
        @Test
        fun `ugyldig konfigurasjon feiler ved oppstart`() {
            shouldThrow<IllegalArgumentException> {
                EnhetsregisterClient(
                    allowedHosts = emptySet(),
                    baseUrl = "http://amt-enhetsregister",
                    enhetsregisterApi = object : EnhetsregisterApi {
                        override fun hentVirksomhet(orgNr: String): Virksomhet = error("Skal ikke kalles")
                    },
                )
            }
        }

        @Test
        fun `host matcher eksakt i allowedHosts - oppretter klient`() {
            newClient("https://data.brreg.no")
        }

        @Test
        fun `subdomene av allowed host - oppretter klient`() {
            newClient("https://api.data.brreg.no")
        }

        @Test
        fun `http er tillatt (SSRF-sjekk er pa allowedHosts, ikke skjema)`() {
            newClient("http://data.brreg.no")
        }

        @Test
        fun `host matcher ikke allowedHosts - kaster IllegalArgumentException`() {
            val ex = shouldThrow<IllegalArgumentException> {
                newClient("https://evil.example.com")
            }
            ex.message shouldContain
                "Ugyldig baseUrl for Enhetsregister: baseUrl=https://evil.example.com, host=evil.example.com, allowedHosts=[data.brreg.no]"
        }

        @Test
        fun `host ender pa allowed uten punktum foran - kaster IllegalArgumentException`() {
            val ex = shouldThrow<IllegalArgumentException> {
                // "evildata.brreg.no" inneholder "data.brreg.no" men er ikke et ekte subdomene
                newClient("https://evildata.brreg.no", allowedHosts = setOf("data.brreg.no"))
            }
            ex.message shouldContain "Ugyldig baseUrl for Enhetsregister: " +
                "baseUrl=https://evildata.brreg.no, host=evildata.brreg.no, allowedHosts=[data.brreg.no]"
        }

        @Test
        fun `ugyldig URL - kaster IllegalArgumentException`() {
            val ex = shouldThrow<IllegalArgumentException> {
                newClient("ikke en url")
            }
            ex.message shouldContain "Ugyldig baseUrl"
        }

        @Test
        fun `tom allowedHosts - kaster IllegalArgumentException`() {
            val ex = shouldThrow<IllegalArgumentException> {
                newClient("https://data.brreg.no", allowedHosts = emptySet())
            }
            ex.message shouldContain "allowedHosts"
        }

        @Test
        fun `flere allowedHosts, matcher en - oppretter klient`() {
            newClient(
                baseUrl = "http://amt-enhetsregister.amt.svc.cluster.local",
                allowedHosts = setOf("data.brreg.no", "amt-enhetsregister.amt.svc.cluster.local"),
            )
        }

        private fun newClient(
            baseUrl: String,
            allowedHosts: Set<String> = setOf("data.brreg.no"),
        ) = EnhetsregisterClient(
            allowedHosts = allowedHosts,
            baseUrl = baseUrl,
            enhetsregisterApi = object : EnhetsregisterApi {
                override fun hentVirksomhet(orgNr: String): Virksomhet = error("Skal ikke kalles")
            },
        )
    }
}
