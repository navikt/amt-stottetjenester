package no.nav.amt.person.service.clients.pdl

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.BEHANDLINGSNUMMER_HEADER
import no.nav.amt.person.service.clients.BEHANDLINGSNUMMER_HEADER_VALUE
import no.nav.amt.person.service.clients.GEN_TEMA_HEADER_VALUE
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import no.nav.amt.person.service.clients.TEMA_HEADER
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.ERROR_PREFIX
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.NULL_ERROR
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.flereFeilRespons
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.fodselsarRespons
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.gyldigRespons
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.minimalFeilRespons
import no.nav.amt.person.service.clients.pdl.PdlClientTestData.telefonResponse
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.TestData.postnumreInTest
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Personident
import no.nav.amt.person.service.poststed.PoststedRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(PdlClient::class)
class PdlClientTest(
    private val client: PdlClient,
) : RestClientTestBase("pdl-api") {
    @MockkBean
    private lateinit var poststedRepository: PoststedRepository

    @BeforeEach
    fun setUp() {
        every { poststedRepository.getPoststeder(any()) } returns postnumreInTest.toList()
    }

    @Nested
    inner class HentPersonTests {
        @Test
        fun `hentPerson - gyldig respons - skal lage riktig request og parse pdl person`() {
            server
                .expect(requestTo("http://pdl-api/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pdl-api-token"))
                .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
                .andExpect(header(TEMA_HEADER, GEN_TEMA_HEADER_VALUE))
                .andExpect(header(BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_HEADER_VALUE))
                .andRespond(withSuccess(gyldigRespons, MediaType.APPLICATION_JSON))

            val pdlPerson = client.hentPerson("FNR")

            pdlPerson.fornavn shouldBe "Tester"
            pdlPerson.mellomnavn shouldBe "Test"
            pdlPerson.etternavn shouldBe "Testersen"
            pdlPerson.telefonnummer shouldBe "+4712345678"
            pdlPerson.adresse
                ?.bostedsadresse
                ?.matrikkeladresse
                ?.tilleggsnavn shouldBe "Storgården"
            pdlPerson.adresse
                ?.bostedsadresse
                ?.matrikkeladresse
                ?.postnummer shouldBe "0484"
            pdlPerson.adresse
                ?.bostedsadresse
                ?.matrikkeladresse
                ?.poststed shouldBe "OSLO"
            pdlPerson.adresse?.oppholdsadresse shouldBe null
            pdlPerson.adresse
                ?.kontaktadresse
                ?.postboksadresse
                ?.postboks shouldBe "Postboks 1234"

            val ident = pdlPerson.identer.first()
            assertSoftly(ident) {
                type shouldBe IdentType.FOLKEREGISTERIDENT
                historisk shouldBe false
                it.ident shouldBe "00000000000"
            }
        }

        @Test
        fun `hentPerson - data mangler - skal kaste exception`() {
            server.expect(method(HttpMethod.POST)).andRespond(
                withSuccess(
                    """{"errors": [{"message": "Noe gikk galt"}], "data": null}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

            val exception = shouldThrow<RuntimeException> {
                client.hentPerson("FNR")
            }

            exception.message shouldBe "$ERROR_PREFIX$NULL_ERROR- Noe gikk galt (code: null details: null)\n"
        }

        @Test
        fun `hentPerson - Detaljert respons - skal kaste exception med noe detaljert informasjon`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess(minimalFeilRespons, MediaType.APPLICATION_JSON))

            val exception = shouldThrow<RuntimeException> {
                client.hentPerson("FNR")
            }

            exception.message shouldBe
                ERROR_PREFIX + NULL_ERROR +
                "- Ikke tilgang til å se person (code: unauthorized details: PdlErrorDetails(type=abac-deny, cause=cause-0001-manglerrolle, policy=adressebeskyttelse_strengt_fortrolig_adresse))\n"
        }

        @Test
        fun `hentPerson - Flere feil i respons - skal kaste exception med noe detaljert informasjon`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess(flereFeilRespons, MediaType.APPLICATION_JSON))

            val exception = shouldThrow<RuntimeException> {
                client.hentPerson("FNR")
            }

            exception.message shouldBe
                ERROR_PREFIX + NULL_ERROR + "- Ikke tilgang til å se person (code: unauthorized details: PdlErrorDetails(type=abac-deny, " +
                "cause=cause-0001-manglerrolle, policy=adressebeskyttelse_strengt_fortrolig_adresse))\n" +
                "- Test (code: unauthorized details: PdlErrorDetails(type=abac-deny, cause=cause-0001-manglerrolle, " +
                "policy=adressebeskyttelse_strengt_fortrolig_adresse))\n"
        }
    }

    @Test
    fun `hentIdenter skal lage riktig request og parse response`() {
        val personident1 = Personident(TestData.randomIdent(), false, IdentType.FOLKEREGISTERIDENT)
        val personident2 = Personident(TestData.randomIdent(), true, IdentType.FOLKEREGISTERIDENT)

        server
            .expect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pdl-api-token"))
            .andExpect(header(TEMA_HEADER, GEN_TEMA_HEADER_VALUE))
            .andRespond(
                withSuccess(
                    """
                    {
                        "errors": null,
                        "data": {
                            "hentIdenter": {
                              "identer": [
                                { "ident": "${personident1.ident}", "historisk": ${personident1.historisk}, "gruppe": "${personident1.type.name}" },
                                { "ident": "${personident2.ident}", "historisk": ${personident2.historisk}, "gruppe": "${personident2.type.name}" }
                              ]
                            }
                        }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        client.hentIdenter(personident2.ident) shouldBe listOf(personident1, personident2)
    }

    @Test
    fun `hentIdenter - hentIdenter er null - skal kaste exception`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"errors": null, "data": {"hentIdenter": null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val exception = shouldThrow<RuntimeException> {
            client.hentIdenter("FNR")
        }

        exception.message shouldBe "PDL respons inneholder ikke data"
    }

    @Nested
    inner class HentTelefonTests {
        @Test
        fun `hentTelefon - person har telefon - returnerer telefon`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess(telefonResponse, MediaType.APPLICATION_JSON))

            client.hentTelefon("FNR") shouldBe "+4712345678"
        }

        @Test
        fun `hentTelefon - person uten telefon - returnerer null`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": null, "data": {"hentPerson": {"telefonnummer": []}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            client.hentTelefon("FNR") shouldBe null
        }

        @Test
        fun `hentTelefon - tom errors-liste - skal ikke kaste exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": [], "data": {"hentPerson": {"telefonnummer": []}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            client.hentTelefon("FNR") shouldBe null
        }

        @Test
        fun `hentTelefon - data mangler - skal kaste exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": [{"message": "Noe gikk galt"}], "data": null}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val exception = shouldThrow<RuntimeException> {
                client.hentTelefon("FNR")
            }

            exception.message shouldBe "$ERROR_PREFIX$NULL_ERROR- Noe gikk galt (code: null details: null)\n"
        }
    }

    @Nested
    inner class HentPersonFodselsarTests {
        @Test
        fun `hentPersonFodselsar - person har fodselsar - returnerer fodselsar`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess(fodselsarRespons, MediaType.APPLICATION_JSON))

            client.hentPersonFodselsar("FNR") shouldBe 1976
        }

        @Test
        fun `hentPersonFodselsar - data mangler - skal kaste exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": [{"message": "Noe gikk galt"}], "data": null}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val exception = shouldThrow<RuntimeException> {
                client.hentPersonFodselsar("FNR")
            }

            exception.message shouldBe "$ERROR_PREFIX$NULL_ERROR- Noe gikk galt (code: null details: null)\n"
        }

        @Test
        fun `hentPersonFodselsar - person mangler foedselsdato - skal kaste exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": null, "data": {"hentPerson": {"foedselsdato": []}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val exception = shouldThrow<RuntimeException> {
                client.hentPersonFodselsar("FNR")
            }

            exception.message shouldBe "PDL person mangler fodselsdato"
        }
    }

    @Nested
    inner class HentAdressebeskyttelseTests {
        @Test
        fun `hentAdressebeskyttelse - person er beskyttet - returnerer gradering`() {
            server
                .expect(requestTo("http://pdl-api/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pdl-api-token"))
                .andRespond(
                    withSuccess(
                        """{"errors": null, "data": {"hentPerson": {"adressebeskyttelse": [{"gradering": "STRENGT_FORTROLIG"}]}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            client.hentAdressebeskyttelse("FNR") shouldBe AdressebeskyttelseGradering.STRENGT_FORTROLIG
        }

        @Test
        fun `hentAdressebeskyttelse - person er ikke beskyttet - returnerer null`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": null, "data": {"hentPerson": {"adressebeskyttelse": []}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            client.hentAdressebeskyttelse("FNR") shouldBe null
        }

        @Test
        fun `hentAdressebeskyttelse - data mangler - skal kaste exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"errors": [{"message": "Noe gikk galt"}], "data": null}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val exception = shouldThrow<RuntimeException> {
                client.hentAdressebeskyttelse("FNR")
            }

            exception.message shouldBe "$ERROR_PREFIX$NULL_ERROR- Noe gikk galt (code: null details: null)\n"
        }
    }

    @Test
    fun `executeQuery - HTTP 500 fra PDL - skal kaste exception`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withServerError())

        shouldThrow<RetryableUpstreamServiceException> {
            client.hentPerson("FNR")
        }
    }

    @Test
    fun `executeQuery - tom HTTP 200-respons - skal kaste UpstreamServiceException med status`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val exception = shouldThrow<UpstreamServiceException> {
            client.hentPerson("FNR")
        }

        exception.statusCode shouldBe 200
    }
}
