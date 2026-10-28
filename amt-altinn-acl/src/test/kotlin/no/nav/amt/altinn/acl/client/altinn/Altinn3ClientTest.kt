package no.nav.amt.altinn.acl.client.altinn

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.altinn.acl.client.RestClientTestBase
import no.nav.amt.altinn.acl.client.exception.AltinnClientException
import no.nav.amt.altinn.acl.config.ALTINN3_CLIENT_ID
import no.nav.amt.altinn.acl.domain.RolleType
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

@RestClientTest(Altinn3Client::class)
class Altinn3ClientTest(
    private val sut: Altinn3Client,
) : RestClientTestBase(ALTINN3_CLIENT_ID) {
    @Test
    fun `hentRoller - flere tilganger - parser response riktig`() {
        // Arrange
        server
            .expect(requestTo(AUTHORIZED_PARTIES_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"value":"123456","type":"urn:altinn:person:identifier-no"}"""))
            .andRespond(
                withSuccess(
                    """
                    [
                      {
                        "organizationNumber": "123456789",
                        "authorizedResources": [
                          "${RolleType.KOORDINATOR.resourceId}",
                          "${RolleType.VEILEDER.resourceId}"
                        ],
                        "subunits": []
                      },
                      {
                        "organizationNumber": "987654321",
                        "authorizedResources": [],
                        "subunits": [
                          {
                            "organizationNumber": "111222333",
                            "authorizedResources": ["${RolleType.VEILEDER.resourceId}"],
                            "subunits": []
                          }
                        ]
                      },
                      {
                        "organizationNumber": "456789012",
                        "authorizedResources": ["${RolleType.KOORDINATOR.resourceId}"],
                        "subunits": [
                          {
                            "organizationNumber": "333444555",
                            "authorizedResources": ["${RolleType.VEILEDER.resourceId}"],
                            "subunits": [
                              {
                                "organizationNumber": "666777888",
                                "authorizedResources": ["${RolleType.KOORDINATOR.resourceId}"],
                                "subunits": []
                              }
                            ]
                          }
                        ]
                      }
                    ]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // Act
        val organisasjoner = sut.hentRoller("123456", RolleType.entries)

        // Assert
        organisasjoner[RolleType.KOORDINATOR].shouldNotBeNull() shouldBe
            listOf("123456789", "456789012", "666777888")

        organisasjoner[RolleType.VEILEDER].shouldNotBeNull() shouldBe
            listOf("123456789", "111222333", "333444555")
    }

    @Test
    fun `hentRoller - Altinn svarer med feil - kaster sanitert feil`() {
        // Arrange
        val norskIdent = "12345678901"
        server
            .expect(requestTo(AUTHORIZED_PARTIES_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"norskIdent":"$norskIdent"}"""),
            )

        // Act
        val exception = shouldThrow<AltinnClientException> {
            sut.hentRoller(norskIdent, RolleType.entries)
        }

        // Assert
        assertSoftly(exception) {
            message shouldBe "Klarte ikke å hente organisasjoner fra Altinn, status=500"
            statusCode shouldBe 500
            message.shouldNotBeNull() shouldNotContain norskIdent
            cause shouldBe null
        }
    }

    private companion object {
        const val AUTHORIZED_PARTIES_URL =
            "http://localhost:9999/altinn/accessmanagement/api/v1/resourceowner/authorizedparties"
    }
}
