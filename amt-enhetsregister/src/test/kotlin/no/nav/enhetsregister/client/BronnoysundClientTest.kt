package no.nav.enhetsregister.client

import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.enhetsregister.domain.EnhetOppdatering
import no.nav.enhetsregister.domain.EnhetOppdateringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.stream.Stream

@RestClientTest(value = [BronnoysundClient::class, BronnoysundBulkDownloader::class])
@Import(BronnoysundClientConfig::class, BronnoysundClientTestConfig::class)
@TestPropertySource(
    properties = [
        "spring.http.serviceclient.brreg-api.base-url=http://brreg-api",
        "spring.test.restclient.mockrestserviceserver.enabled=false",
    ],
)
class BronnoysundClientTest(
    @Autowired private val client: BronnoysundClient,
    @Autowired private val testConfig: BronnoysundClientTestConfig,
) {
    companion object {
        @JvmStatic
        fun enhetOppdateringDtoEndringstypeTilDomeneTypeSource(): Stream<Arguments?> = Stream.of<Arguments?>(
            Arguments.of("Ny", EnhetOppdateringType.NY),
            Arguments.of("Ukjent", EnhetOppdateringType.UKJENT),
            Arguments.of("Fjernet", EnhetOppdateringType.FJERNET),
            Arguments.of("Sletting", EnhetOppdateringType.SLETTING),
            Arguments.of("Endring", EnhetOppdateringType.ENDRING),
        )
    }

    private val server get() = testConfig.getMock("brreg-api")

    @BeforeEach
    fun resetServer() = server.reset()

    @AfterEach
    fun verifyRequests() = server.verify()

    @Nested
    inner class HentModerenhet {
        @Test
        fun `hentModerenhet skal lage riktig request og parse response`() {
            expectFixture(
                filename = "client/hent-moderenhet-response.json",
                path = "/enhetsregisteret/api/enheter/998877443",
            )

            val moderenhet = client.hentModerenhet("998877443")

            assertNotNull(moderenhet)
            assertEquals("998877443", moderenhet?.organisasjonsnummer)
            assertEquals("Moderenhet 1", moderenhet?.navn)
        }

        @Test
        fun `hentModerenhet skal returnere null for status 410 GONE`() {
            expectStatus("/enhetsregisteret/api/enheter/12345678", HttpStatus.GONE)

            assertNull(client.hentModerenhet("12345678"))
        }

        @Test
        fun `hentModerenhet skal returnere null for status 404 NOT_FOUND`() {
            expectStatus("/enhetsregisteret/api/enheter/12345678", HttpStatus.NOT_FOUND)

            assertNull(client.hentModerenhet("12345678"))
        }

        @Test
        fun `hentModerenhet skal kaste UpstreamServiceException ved tom HTTP 200-respons`() {
            expectStatus("/enhetsregisteret/api/enheter/12345678", HttpStatus.OK)

            val thrown =
                assertThrows<UpstreamServiceException> {
                    client.hentModerenhet("12345678")
                }

            assertEquals(HttpStatus.OK.value(), thrown.statusCode)
        }

        @Test
        fun `hentModerenhet skal kaste feil for status 500 INTERNAL_SERVER_ERROR`() {
            expectStatus("/enhetsregisteret/api/enheter/12345678", HttpStatus.INTERNAL_SERVER_ERROR)

            val thrown =
                assertThrows<RetryableUpstreamServiceException> {
                    client.hentModerenhet("12345678")
                }

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), thrown.statusCode)
        }
    }

    @Nested
    inner class HentUnderenhet {
        @Test
        fun `hentUnderenhet skal lage riktig request og parse response`() {
            expectFixture(
                filename = "client/hent-underenhet-response.json",
                path = "/enhetsregisteret/api/underenheter/12345678",
            )

            val underenhet = client.hentUnderenhet("12345678")

            assertNotNull(underenhet)
            assertEquals("12345678", underenhet?.organisasjonsnummer)
            assertEquals("Underenhet 1", underenhet?.navn)
            assertEquals("9988564", underenhet?.overordnetEnhet)
        }

        @Test
        fun `hentUnderenhet skal returnere null for status 410 GONE`() {
            expectStatus("/enhetsregisteret/api/underenheter/12345678", HttpStatus.GONE)

            assertNull(client.hentUnderenhet("12345678"))
        }

        @Test
        fun `hentUnderenhet skal returnere null for status 404 NOT_FOUND`() {
            expectStatus("/enhetsregisteret/api/underenheter/12345678", HttpStatus.NOT_FOUND)

            assertNull(client.hentUnderenhet("12345678"))
        }

        @Test
        fun `hentUnderenhet skal kaste feil for status 500 INTERNAL_SERVER_ERROR`() {
            expectStatus("/enhetsregisteret/api/underenheter/12345678", HttpStatus.INTERNAL_SERVER_ERROR)

            val thrown =
                assertThrows<RetryableUpstreamServiceException> {
                    client.hentUnderenhet("12345678")
                }

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), thrown.statusCode)
        }
    }

    @Nested
    inner class HentModerenhetOppdateringer {
        private val path = "/enhetsregisteret/api/oppdateringer/enheter?oppdateringsid=10&size=100"

        @Test
        fun `hentModerenhetOppdateringer skal kaste feil for status 410 GONE`() {
            expectStatus(path, HttpStatus.GONE)

            val thrown =
                assertThrows<UpstreamServiceException> {
                    client.hentModerenhetOppdateringer(fraOppdateringId = 10, size = 100)
                }

            assertEquals(HttpStatus.GONE.value(), thrown.statusCode)
        }

        @Test
        fun `hentModerenhetOppdateringer skal returnere tom liste hvis _embedded mangler`() {
            expectJson(path, """{"_embedded":null}""")

            assertEquals(
                emptyList<EnhetOppdatering>(),
                client.hentModerenhetOppdateringer(fraOppdateringId = 10, size = 100),
            )
        }

        @Test
        fun `hentModerenhetOppdateringer skal lage riktig request og parse response`() {
            expectFixture("client/hent-moderenhet-oppdateringer-response.json", path)

            val oppdateringer = client.hentModerenhetOppdateringer(10, 100)
            val expectedDate =
                ZonedDateTime.of(
                    LocalDateTime.of(2018, 5, 14, 21, 53, 0),
                    ZoneOffset.UTC,
                )

            assertEquals(2, oppdateringer.size)
            assertEquals(123867, oppdateringer[0].oppdateringId)
            assertEquals(expectedDate, oppdateringer[0].dato)
            assertEquals("9876543", oppdateringer[0].organisasjonsnummer)
            assertEquals(EnhetOppdateringType.UKJENT, oppdateringer[0].endringstype)
            assertEquals(123868, oppdateringer[1].oppdateringId)
            assertEquals(expectedDate, oppdateringer[1].dato)
            assertEquals("88445533", oppdateringer[1].organisasjonsnummer)
            assertEquals(EnhetOppdateringType.NY, oppdateringer[1].endringstype)
        }
    }

    @Nested
    inner class HentUnderenhetOppdateringer {
        private val path = "/enhetsregisteret/api/oppdateringer/underenheter?oppdateringsid=10&size=100"

        @Test
        fun `hentUnderenhetOppdateringer skal kaste feil for status 410 GONE`() {
            expectStatus(path, HttpStatus.GONE)

            val thrown =
                assertThrows<UpstreamServiceException> {
                    client.hentUnderenhetOppdateringer(fraOppdateringId = 10, size = 100)
                }

            assertEquals(HttpStatus.GONE.value(), thrown.statusCode)
        }

        @Test
        fun `hentUnderenhetOppdateringer skal returnere tom liste hvis _embedded mangler`() {
            expectJson(path, """{"_embedded":null}""")

            assertEquals(
                emptyList<EnhetOppdatering>(),
                client.hentUnderenhetOppdateringer(fraOppdateringId = 10, size = 100),
            )
        }

        @Test
        fun `hentUnderenhetOppdateringer skal lage riktig request og parse response`() {
            expectFixture("client/hent-underenhet-oppdateringer-response.json", path)

            val oppdateringer = client.hentUnderenhetOppdateringer(10, 100)
            val expectedDate =
                ZonedDateTime.of(
                    LocalDateTime.of(2018, 5, 14, 21, 53, 0),
                    ZoneOffset.UTC,
                )

            assertEquals(2, oppdateringer.size)
            assertEquals(123867, oppdateringer[0].oppdateringId)
            assertEquals(expectedDate, oppdateringer[0].dato)
            assertEquals("9876543", oppdateringer[0].organisasjonsnummer)
            assertEquals(EnhetOppdateringType.UKJENT, oppdateringer[0].endringstype)
            assertEquals(123868, oppdateringer[1].oppdateringId)
            assertEquals(expectedDate, oppdateringer[1].dato)
            assertEquals("88445533", oppdateringer[1].organisasjonsnummer)
            assertEquals(EnhetOppdateringType.NY, oppdateringer[1].endringstype)
        }
    }

    @Nested
    inner class EnhetOppdateringDtoToDomain {
        @ParameterizedTest(name = "{0} skal returnere {1}")
        @MethodSource("no.nav.enhetsregister.client.BronnoysundClientTest#enhetOppdateringDtoEndringstypeTilDomeneTypeSource")
        fun `toDomain mapper endringstype`(
            value: String,
            expectedType: EnhetOppdateringType,
        ) {
            assertThat(
                EnhetOppdateringDto(
                    oppdateringsid = 1,
                    dato = "2024-01-01T00:00:00Z",
                    organisasjonsnummer = "123456789",
                    endringstype = value,
                ).toDomain().endringstype,
            ).isEqualTo(expectedType)
        }

        @Test
        fun `toDomain skal kaste feil ved ukjent endringstype`() {
            val thrown =
                assertThrows<IllegalArgumentException> {
                    EnhetOppdateringDto(
                        oppdateringsid = 1,
                        dato = "2024-01-01T00:00:00Z",
                        organisasjonsnummer = "123456789",
                        endringstype = "~unknown~",
                    ).toDomain()
                }

            assertEquals("Ukjent EnhetOppdateringType: ~unknown~", thrown.message)
        }
    }

    private fun expectFixture(
        filename: String,
        path: String,
    ) = server
        .expect(requestTo("http://brreg-api$path"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(ClassPathResource(filename), MediaType.APPLICATION_JSON))

    private fun expectJson(
        path: String,
        body: String,
    ) = server
        .expect(requestTo("http://brreg-api$path"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

    private fun expectStatus(
        path: String,
        status: HttpStatus,
    ) = server
        .expect(requestTo("http://brreg-api$path"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(status))
}
