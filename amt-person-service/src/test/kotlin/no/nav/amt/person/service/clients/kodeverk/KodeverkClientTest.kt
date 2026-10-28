package no.nav.amt.person.service.clients.kodeverk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import no.nav.amt.person.service.clients.kodeverk.GetKodeverkKoderBetydningerResponse.Betydning
import no.nav.amt.person.service.clients.kodeverk.GetKodeverkKoderBetydningerResponse.Betydning.Beskrivelse
import no.nav.amt.person.service.poststed.Postnummer
import org.hamcrest.CoreMatchers.containsString
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

@RestClientTest(KodeverkClient::class)
class KodeverkClientTest(
    private val sut: KodeverkClient,
) : RestClientTestBase("kodeverk-api") {
    @Nested
    inner class HentKodeverk {
        @Test
        fun `skal sende riktige headere og query-parametre, og parse respons`() {
            // Arrange
            server
                .expect(requestTo(containsString("http://kodeverk-api/api/v1/kodeverk/Postnummer/koder/betydninger")))
                .andExpect(requestTo(containsString("ekskluderUgyldige=true")))
                .andExpect(requestTo(containsString("oppslagsdato=")))
                .andExpect(requestTo(containsString("spraak=nb")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kodeverk-api-token"))
                .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
                .andRespond(
                    withSuccess(
                        javaClass.getResourceAsStream("/kodeverkrespons.json")!!.bufferedReader().readText(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            // Act
            val postnummer = sut.hentKodeverk()

            // Assert
            postnummer.find { it.postnummer == "3831" }?.poststed shouldBe "ULEFOSS"
        }

        @Test
        fun `500 fra kodeverk - kaster RetryableUpstreamServiceException`() {
            // Arrange
            server
                .expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            // Act
            val exception = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentKodeverk()
            }

            // Assert
            exception.statusCode shouldBe 500
        }

        @Test
        fun `tomt svar fra kodeverk - kaster UpstreamServiceException med status`() {
            // Arrange
            server
                .expect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

            // Act
            val exception = shouldThrow<UpstreamServiceException> {
                sut.hentKodeverk()
            }

            // Assert
            exception.statusCode shouldBe HttpStatus.OK.value()
        }
    }

    @Nested
    inner class ToPostnummerListe {
        @Test
        fun `mapper postnummer og bruker norsk term fra første betydning`() {
            // Arrange
            val response = GetKodeverkKoderBetydningerResponse(
                betydninger = mapOf(
                    "0123" to listOf(
                        Betydning(
                            beskrivelser = mapOf(
                                "nb" to Beskrivelse(term = "OSLO"),
                                "en" to Beskrivelse(term = "OSLO ENGLISH"),
                            ),
                        ),
                        Betydning(
                            beskrivelser = mapOf(
                                "nb" to Beskrivelse(term = "ALTERNATIVT POSTSTED"),
                            ),
                        ),
                    ),
                    "4567" to listOf(
                        Betydning(
                            beskrivelser = mapOf(
                                "nb" to Beskrivelse(term = "ASKER"),
                            ),
                        ),
                    ),
                ),
            )

            // Act
            val postnummer = sut.run { response.toPostnummerListe() }

            // Assert
            postnummer shouldBe listOf(
                Postnummer(postnummer = "0123", poststed = "OSLO"),
                Postnummer(postnummer = "4567", poststed = "ASKER"),
            )
        }

        @Test
        fun `kaster exception når kode mangler betydning`() {
            // Arrange
            val response = GetKodeverkKoderBetydningerResponse(
                betydninger = mapOf("0123" to emptyList()),
            )

            // Act
            val exception = shouldThrow<RuntimeException> {
                sut.run { response.toPostnummerListe() }
            }

            // Assert
            exception.message shouldBe "Kode 0123 mangler term"
        }

        @Test
        fun `kaster exception når første betydning mangler norsk term`() {
            // Arrange
            val response = GetKodeverkKoderBetydningerResponse(
                betydninger = mapOf(
                    "0123" to listOf(
                        Betydning(
                            beskrivelser = emptyMap(),
                        ),
                        Betydning(
                            beskrivelser = mapOf(
                                "nb" to Beskrivelse(term = "OSLO"),
                            ),
                        ),
                    ),
                ),
            )

            // Act
            val exception = shouldThrow<RuntimeException> {
                sut.run { response.toPostnummerListe() }
            }

            // Assert
            exception.message shouldBe "Kode 0123 mangler term"
        }
    }
}
