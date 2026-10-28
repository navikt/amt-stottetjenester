package no.nav.amt.altinn.acl.client.maskinporten

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.client.RestClientTestBase
import no.nav.amt.altinn.acl.client.exception.MaskinportenTokenException
import no.nav.amt.altinn.acl.config.MASKINPORTEN_CLIENT_ID
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@RestClientTest(MaskinportenTokenApi::class)
class MaskinportenTokenClientTest(
    maskinportenTokenRequest: MultiValueMap<String, String>,
    maskinportenTokenApi: MaskinportenTokenApi,
) : RestClientTestBase(MASKINPORTEN_CLIENT_ID) {
    private val sut = MaskinportenTokenClient(maskinportenTokenRequest, maskinportenTokenApi)

    @Test
    fun `henter access token fra Nais med riktige skjemadata`() {
        val expectedForm = LinkedMultiValueMap<String, String>().apply {
            add("identity_provider", "maskinporten")
            add("target", "altinn:accessmanagement/authorizedparties.resourceowner")
            add("resource", "http://localhost:9999/altinn")
        }
        server
            .expect(requestTo("http://localhost:9999/maskinporten/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formData(expectedForm))
            .andRespond(withSuccess("""{"access_token":"maskinporten-access-token"}""", MediaType.APPLICATION_JSON))

        sut.getAccessToken() shouldBe "maskinporten-access-token"
    }

    @Test
    fun `feil fra Nais sitt token-endepunkt gir statuskode og OAuth-feilkode`() {
        server
            .expect(requestTo("http://localhost:9999/maskinporten/token"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"invalid_target","error_description":"scope ikke registrert"}"""),
            )

        val exception = shouldThrow<MaskinportenTokenException> {
            sut.getAccessToken()
        }

        exception.message shouldBe
            "Klarte ikke å hente Maskinporten-token code=400 error=invalid_target"
        exception.statusCode shouldBe 400
        exception.errorCode shouldBe "invalid_target"
        exception.cause shouldBe null
    }

    @Test
    fun `feil uten lesbar OAuth-body gir statuskode og ukjent feilkode`() {
        server
            .expect(requestTo("http://localhost:9999/maskinporten/token"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("ikke json"))

        val exception = shouldThrow<MaskinportenTokenException> {
            sut.getAccessToken()
        }

        exception.message shouldBe "Klarte ikke å hente Maskinporten-token code=500 error=unknown"
        exception.statusCode shouldBe 500
        exception.errorCode shouldBe "unknown"
    }

    @Test
    fun `ukjent OAuth-feilkode og beskrivelse eksponeres ikke i exception`() {
        server
            .expect(requestTo("http://localhost:9999/maskinporten/token"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"ukjent sensitiv verdi","error_description":"sensitiv diagnostikk"}"""),
            )

        val exception = shouldThrow<MaskinportenTokenException> {
            sut.getAccessToken()
        }

        exception.message shouldBe "Klarte ikke å hente Maskinporten-token code=400 error=unknown"
        exception.statusCode shouldBe 400
        exception.errorCode shouldBe "unknown"
    }
}
