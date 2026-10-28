package no.nav.amt.altinn.acl.client.maskinporten

import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.client.RestClientTestBase
import no.nav.amt.altinn.acl.config.MASKINPORTEN_CLIENT_ID
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@RestClientTest(MaskinportenTokenApi::class)
class MaskinportenTokenApiTest(
    private val maskinportenTokenRequest: MultiValueMap<String, String>,
    private val sut: MaskinportenTokenApi,
) : RestClientTestBase(MASKINPORTEN_CLIENT_ID) {
    @Test
    fun `hentAccessToken - sender riktig request og leser token`() {
        // Arrange
        val expectedForm = LinkedMultiValueMap<String, String>().apply {
            add("identity_provider", "maskinporten")
            add("target", "altinn:accessmanagement/authorizedparties.resourceowner")
            add("resource", "http://localhost:9999/altinn")
        }

        server
            .expect(requestTo("http://localhost:9999/maskinporten/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(content().formData(expectedForm))
            .andRespond(
                withSuccess(
                    """
                    {
                        "access_token": "~access_token~"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // Act
        val tokenResponse = sut.hentAccessToken(maskinportenTokenRequest)

        // Assert
        tokenResponse.statusCode shouldBe HttpStatus.OK
        tokenResponse.body shouldBe MaskinportenTokenApi.MaskinportenTokenResponse(accessToken = "~access_token~")
    }
}
