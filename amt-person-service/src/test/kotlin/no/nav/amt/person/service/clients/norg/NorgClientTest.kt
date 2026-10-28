package no.nav.amt.person.service.clients.norg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.NORG_API_CLIENT_ID
import no.nav.amt.person.service.clients.RestClientTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(NorgClient::class)
class NorgClientTest(
    private val sut: NorgClient,
) : RestClientTestBase(NORG_API_CLIENT_ID) {
    @Nested
    inner class HentNavEnhet {
        @Test
        fun `hentNavEnhet - skal lage riktig request og parse respons`() {
            // Arrange
            server
                .expect(requestTo("http://norg/norg2/api/v1/enhet/1234"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "navn": "Nav Testheim",
                          "enhetNr": "1234"
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            // Act
            val enhet = sut.hentNavEnhet("1234")

            // Assert
            enhet?.enhetNr shouldBe "1234"
            enhet?.navn shouldBe "Nav Testheim"
        }

        @Test
        fun `hentNavEnhet - 404 fra norg - returnerer null`() {
            // Arrange
            server
                .expect(requestTo("http://norg/norg2/api/v1/enhet/4321"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            // Act
            val enhet = sut.hentNavEnhet("4321")

            // Assert
            enhet.shouldBeNull()
        }

        @Test
        fun `hentNavEnhet - 500 fra norg - kaster exception`() {
            // Arrange
            server
                .expect(requestTo("http://norg/norg2/api/v1/enhet/9999"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            // Act
            val exception = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentNavEnhet("9999")
            }

            // Assert
            exception.statusCode shouldBe 500
            exception.message shouldBe "Kall mot NORG feilet under hent enhet (HTTP 500)"
        }

        @Test
        fun `hentNavEnhet - tom HTTP 200-respons - kaster UpstreamServiceException med status`() {
            server
                .expect(requestTo("http://norg/norg2/api/v1/enhet/1234"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

            val exception = shouldThrow<UpstreamServiceException> {
                sut.hentNavEnhet("1234")
            }

            exception.statusCode shouldBe HttpStatus.OK.value()
        }

        @Test
        fun `hentNavEnhet - ugyldig enhetId - kaster IllegalArgumentException`() {
            // Arrange
            val ugyldigEnhetId = "12"

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                sut.hentNavEnhet(ugyldigEnhetId)
            }

            // Assert
            exception.message shouldBe "Ugyldig enhetId-format"
        }
    }

    @Nested
    inner class HentNavEnheter {
        @Test
        fun `hentNavEnheter - skal lage riktig request og parse respons`() {
            // Arrange
            server
                .expect(requestTo("http://norg/norg2/api/v1/enhet?enhetsnummerListe=1234,5678"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """
                        [
                          { "navn": "Nav Testheim", "enhetNr": "1234" },
                          { "navn": "Nav Annet",    "enhetNr": "5678" }
                        ]
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            // Act
            val enheter = sut.hentNavEnheter(listOf("1234", "5678"))

            // Assert
            enheter shouldBe listOf(
                NorgNavEnhetDto(navn = "Nav Testheim", enhetNr = "1234"),
                NorgNavEnhetDto(navn = "Nav Annet", enhetNr = "5678"),
            )
        }

        @Test
        fun `hentNavEnheter - 500 fra norg - kaster exception`() {
            // Arrange
            server
                .expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            // Act
            val exception = shouldThrow<RuntimeException> {
                sut.hentNavEnheter(listOf("1234"))
            }

            // Assert
            exception.message shouldBe "Kall mot NORG feilet under hent enheter (HTTP 500)"
        }

        @Test
        fun `hentNavEnheter - ugyldig enhetId - kaster IllegalArgumentException`() {
            // Arrange
            val ugyldigEnhetId = "12"

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                sut.hentNavEnheter(listOf(ugyldigEnhetId))
            }

            // Assert
            exception.message shouldBe "Ugyldig enhetId-format"
        }
    }
}
