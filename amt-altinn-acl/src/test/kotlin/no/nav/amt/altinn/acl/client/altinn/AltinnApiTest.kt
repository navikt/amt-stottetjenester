package no.nav.amt.altinn.acl.client.altinn

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.client.RestClientTestBase
import no.nav.amt.altinn.acl.config.ALTINN3_CLIENT_ID
import no.nav.amt.altinn.acl.domain.RolleType
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
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

/**
 * Tester HTTP-kontrakten til AltinnApi mot en ekte deklarativ RestClient-proxy (via @ImportHttpServices),
 * i stedet for å mocke AltinnApi-grensesnittet direkte. Dette verifiserer at:
 * - riktig URL, HTTP-metode og request-body sendes,
 * - Altinn-klientgruppen legger på bearer-token,
 * - JSON-responsen mappes til AuthorizedParty.
 */
@RestClientTest(AltinnApi::class)
class AltinnApiTest(
    private val sut: AltinnApi,
) : RestClientTestBase(ALTINN3_CLIENT_ID) {
    @Test
    fun `hentAuthorizedParties - sender riktig request med Bearer-token-header`() {
        // Arrange
        server
            .expect(requestTo("http://localhost:9999/altinn/accessmanagement/api/v1/resourceowner/authorizedparties"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer altinn3-token"))
            .andExpect(content().json("""{"value":"12345678910","type":"urn:altinn:person:identifier-no"}"""))
            .andRespond(
                withSuccess(
                    """
                    [
                      {
                        "organizationNumber": "123456789",
                        "authorizedResources": ["${RolleType.KOORDINATOR.resourceId}"],
                        "subunits": []
                      }
                    ]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // Act
        val responseEntity = sut.hentAuthorizedParties(AuthorizedPartiesRequest("12345678910"))

        // Assert
        responseEntity.statusCode shouldBe HttpStatus.OK
        val authorizedParties: List<AuthorizedParty> = responseEntity.body.shouldNotBeNull()

        authorizedParties shouldBe listOf(
            AuthorizedParty(
                organizationNumber = "123456789",
                authorizedResources = setOf(RolleType.KOORDINATOR.resourceId),
                subunits = emptyList(),
            ),
        )
    }
}
