package no.nav.amt.person.service.clients.nom

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(NomClient::class)
class NomClientTest(
    private val sut: NomClient,
) : RestClientTestBase("nom-api") {
    @Test
    fun `hentNavAnsatt - veileder finnes ikke - returnerer null`() {
        server
            .expect(requestTo("http://nom-api/graphql"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer nom-api-token"))
            .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
            .andRespond(
                withSuccess(
                    hentRessurserResponse(emptyList(), 1),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentNavAnsatt("test") shouldBe null
    }

    @Test
    fun `hentNavAnsatte - veiledere finnes - returnerer veiledere`() {
        val veiledere = listOf(TestData.lagNavAnsatt(), TestData.lagNavAnsatt())

        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess(hentRessurserResponse(veiledere), MediaType.APPLICATION_JSON))

        val faktiskeVeiledere = sut.hentNavAnsatte(veiledere.map { it.navIdent })
        val firstFaktiskVeileder = faktiskeVeiledere.first { it.navIdent == veiledere[0].navIdent }
        val secondFaktiskVeileder = faktiskeVeiledere.first { it.navIdent == veiledere[1].navIdent }

        assertSoftly(firstFaktiskVeileder) {
            navIdent shouldBe veiledere[0].navIdent
            navn shouldBe veiledere[0].navn
            telefonnummer shouldBe veiledere[0].telefon
            epost shouldBe veiledere[0].epost
        }

        assertSoftly(secondFaktiskVeileder) {
            navIdent shouldBe veiledere[1].navIdent
            navn shouldBe veiledere[1].navn
            telefonnummer shouldBe veiledere[1].telefon
            epost shouldBe veiledere[1].epost
        }
    }

    @Test
    fun `hentNavAnsatte - en veileder finnes ikke - returnerer veileder som finnes`() {
        val veileder = TestData.lagNavAnsatt()
        val feilIdent = "Feil Ident"

        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    hentRessurserResponse(listOf(veileder), 1),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val faktiskeVeiledere = sut.hentNavAnsatte(listOf(veileder.navIdent, feilIdent))
        val faktiskVeileder = faktiskeVeiledere.first { it.navIdent == veileder.navIdent }

        faktiskeVeiledere.find { it.navIdent == feilIdent } shouldBe null
        faktiskeVeiledere shouldHaveSize 1

        assertSoftly(faktiskVeileder) {
            navIdent shouldBe veileder.navIdent
            navn shouldBe veileder.navn
            telefonnummer shouldBe veileder.telefon
            epost shouldBe veileder.epost
        }
    }

    @Test
    fun `hentNavAnsatte - visningsnavn er null - bruker fornavn og etternavn`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "ressurser": [{
                          "ressurs": {
                            "navident": "Z123",
                            "visningsnavn": null,
                            "fornavn": "Ola",
                            "etternavn": "Nordmann",
                            "epost": "ola@nav.no",
                            "telefon": [],
                            "orgTilknytning": []
                          },
                          "code": "OK"
                        }]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut.hentNavAnsatte(listOf("Z123"))

        result shouldHaveSize 1
        result.first().navn shouldBe "Ola Nordmann"
    }

    @Test
    fun `hentNavAnsatte - NAV_KONTOR_TELEFON prioriteres over NAV_TJENESTE_TELEFON`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "ressurser": [{
                          "ressurs": {
                            "navident": "Z123",
                            "visningsnavn": "Test",
                            "fornavn": "Test",
                            "etternavn": "Test",
                            "epost": "test@nav.no",
                            "telefon": [
                              { "type": "NAV_TJENESTE_TELEFON", "nummer": "11111111" },
                              { "type": "NAV_KONTOR_TELEFON", "nummer": "22222222" }
                            ],
                            "orgTilknytning": []
                          },
                          "code": "OK"
                        }]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentNavAnsatte(listOf("Z123")).first().telefonnummer shouldBe "22222222"
    }

    @Test
    fun `hentNavAnsatte - ingen telefon - returnerer null`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "ressurser": [{
                          "ressurs": {
                            "navident": "Z123",
                            "visningsnavn": "Test",
                            "fornavn": "Test",
                            "etternavn": "Test",
                            "epost": "test@nav.no",
                            "telefon": [],
                            "orgTilknytning": []
                          },
                          "code": "OK"
                        }]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentNavAnsatte(listOf("Z123")).first().telefonnummer shouldBe null
    }

    @Test
    fun `hentNavAnsatte - data er null - returnerer tom liste`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{ "data": null }""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentNavAnsatte(listOf("Z123")).shouldBeEmpty()
    }

    @Test
    fun `hentNavAnsatte - HTTP 500 - kaster exception`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withServerError())

        val exception = shouldThrow<RetryableUpstreamServiceException> {
            sut.hentNavAnsatte(listOf("Z123"))
        }

        exception.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    @Test
    fun `hentNavAnsatte - tom HTTP 200-respons - kaster UpstreamServiceException med status`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val exception = shouldThrow<UpstreamServiceException> {
            sut.hentNavAnsatte(listOf("Z123"))
        }

        exception.statusCode shouldBe HttpStatus.OK.value()
    }

    companion object {
        private fun hentRessurserResponse(
            veiledere: List<NavAnsattDbo>,
            antallNotFound: Int = 0,
        ): String {
            val ressurser = veiledere.map {
                """
                {
                	"ressurs": {
                		"navident": "${it.navIdent}",
                		"visningsnavn": "${it.navn}",
                		"fornavn": "Fornavn",
                		"etternavn": "Etternavn",
                		"epost": "${it.epost}",
                		"telefon": [{ "type": "NAV_TJENESTE_TELEFON", "nummer": "${it.telefon}" }],
                		"orgTilknytning": [
                			{
                			  "gyldigTom": null,
                			  "orgEnhet": {
                				"remedyEnhetId": "0315"
                			  },
                			  "erDagligOppfolging": true,
                			  "gyldigFom": "2015-01-01"
                			}
                		]
                	},
                	"code": "OK"
                }
                """.trimIndent()
            }

            val notFound = List(antallNotFound) {
                """
                {
                	"code": "NOT_FOUND",
                	"ressurs": null
                }
                """.trimIndent()
            }

            return """
                {
                  "data": {
                	"ressurser": [ ${ressurser.plus(notFound).joinToString { it }} ]
                  }
                }
                """.trimIndent()
        }
    }
}
