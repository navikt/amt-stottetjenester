package no.nav.enhetsregister.client

import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RestClientTest(BronnoysundBulkDownloader::class)
@Import(BronnoysundBulkDownloaderTestConfig::class)
class BronnoysundBulkDownloaderTest(
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val downloader: BronnoysundBulkDownloader,
    @Autowired private val server: MockRestServiceServer,
) {
    @BeforeEach
    fun resetServer() = server.reset()

    @AfterEach
    fun verifyRequests() = server.verify()

    @Test
    fun `hentAlleModerenheter skal kaste feil ved status 410`() {
        expectStatus("/enhetsregisteret/api/enheter/lastned", HttpStatus.GONE)

        val thrown =
            assertThrows<RuntimeException> {
                downloader.hentAlleModerenheter()
            }

        assertEquals("Klarte ikke å laste ned moderenheter. Status: 410", thrown.message)
    }

    @Test
    fun `hentAlleModerenheter skal strømme og dekode gzip-respons`() {
        val expected = listOf(Moderenhet("~organisasjonsnummer~", "~navn~", null))
        expectGzip(
            uri = "/enhetsregisteret/api/enheter/lastned",
            accept = "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
            body =
                objectMapper.writeValueAsBytes(
                    listOf(ModerenhetDto("~organisasjonsnummer~", "~navn~", null)),
                ),
        )

        assertEquals(expected, downloader.hentAlleModerenheter())
    }

    @Test
    fun `hentAlleUnderenheter skal kaste feil ved status 410`() {
        expectStatus("/enhetsregisteret/api/underenheter/lastned", HttpStatus.GONE)

        val thrown =
            assertThrows<RuntimeException> {
                downloader.hentAlleUnderenheter()
            }

        assertEquals("Klarte ikke å laste ned underenheter. Status: 410", thrown.message)
    }

    @Test
    fun `hentAlleUnderenheter skal strømme og dekode gzip-respons`() {
        val expected = listOf(Underenhet("~organisasjonsnummer~", "~navn~", null, null))
        expectGzip(
            uri = "/enhetsregisteret/api/underenheter/lastned",
            accept = "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
            body =
                objectMapper.writeValueAsBytes(
                    listOf(UnderenhetDto("~organisasjonsnummer~", "~navn~", null, null)),
                ),
        )

        assertEquals(expected, downloader.hentAlleUnderenheter())
    }

    private fun expectStatus(
        uri: String,
        status: HttpStatus,
    ) {
        server
            .expect(requestTo("http://brreg-api$uri"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(status))
    }

    private fun expectGzip(
        uri: String,
        accept: String,
        body: ByteArray,
    ) {
        val compressedBody =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { it.write(body) }
                output.toByteArray()
            }

        server
            .expect(requestTo("http://brreg-api$uri"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.ACCEPT, accept))
            .andRespond { MockClientHttpResponse(compressedBody, HttpStatus.OK) }
    }
}
