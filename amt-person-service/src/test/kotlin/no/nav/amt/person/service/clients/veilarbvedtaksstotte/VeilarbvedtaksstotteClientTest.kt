package no.nav.amt.person.service.clients.veilarbvedtaksstotte

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(VeilarbvedtaksstotteClient::class)
class VeilarbvedtaksstotteClientTest(
    private val sut: VeilarbvedtaksstotteClient,
) : RestClientTestBase("veilarbvedtaksstotte") {
    @ParameterizedTest
    @CsvSource(
        "GODE_MULIGHETER, STANDARD_INNSATS",
        "TRENGER_VEILEDNING, SITUASJONSBESTEMT_INNSATS",
        "TRENGER_VEILEDNING_NEDSATT_ARBEIDSEVNE, SPESIELT_TILPASSET_INNSATS",
        "JOBBE_DELVIS, GRADERT_VARIG_TILPASSET_INNSATS",
        "LITEN_MULIGHET_TIL_A_JOBBE, VARIG_TILPASSET_INNSATS",
    )
    fun `hentInnsatsgruppe - konverterer alle innsatsgrupper fra V2 til V1`(
        innsatsgruppeV2: String,
        forventetV1: String,
    ) {
        server
            .expect(
                requestTo("http://veilarbvedtaksstotte/veilarbvedtaksstotte/api/hent-gjeldende-14a-vedtak"),
            ).andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer veilarbvedtaksstotte-token"))
            .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
            .andExpect(content().json("""{"fnr":"$FNR_IN_TEST"}"""))
            .andRespond(withSuccess("""{"innsatsgruppe":"$innsatsgruppeV2"}""", MediaType.APPLICATION_JSON))

        sut.hentInnsatsgruppe(FNR_IN_TEST) shouldBe Innsatsgruppe.valueOf(forventetV1)
    }

    @Test
    fun `hentInnsatsgruppe - bruker har ikke innsatsgruppe - returnerer null`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        sut.hentInnsatsgruppe(FNR_IN_TEST) shouldBe null
    }

    @Test
    fun `hentInnsatsgruppe - manglende tilgang - kaster exception`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val exception = shouldThrow<UpstreamServiceException> {
            sut.hentInnsatsgruppe(FNR_IN_TEST)
        }
        exception.statusCode shouldBe HttpStatus.FORBIDDEN.value()
    }

    @Test
    fun `hentInnsatsgruppe - HTTP 500 - kaster retrybar exception`() {
        server
            .expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = shouldThrow<RetryableUpstreamServiceException> {
            sut.hentInnsatsgruppe(FNR_IN_TEST)
        }

        exception.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    companion object {
        private const val FNR_IN_TEST = "123"
    }
}
