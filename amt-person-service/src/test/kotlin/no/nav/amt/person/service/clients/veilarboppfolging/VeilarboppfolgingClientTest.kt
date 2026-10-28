package no.nav.amt.person.service.clients.veilarboppfolging

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.RestClientTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@RestClientTest(VeilarboppfolgingClient::class)
class VeilarboppfolgingClientTest(
    private val sut: VeilarboppfolgingClient,
    private val objectMapper: ObjectMapper,
) : RestClientTestBase("veilarboppfolging") {
    @Nested
    inner class HentVeilederIdent {
        @Test
        fun `HentVeilederIdent - Skal sende med authorization og treffe riktig URL`() {
            server
                .expect(requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/hent-veileder"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer veilarboppfolging-token"))
                .andExpect(header(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE))
                .andExpect(content().json("""{"fnr":"$FNR_IN_TEST"}"""))
                .andRespond(
                    withSuccess(
                        """{"veilederIdent":"V123"}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            sut.hentVeilederIdent(FNR_IN_TEST)
        }

        @Test
        fun `HentVeilederIdent - Bruker finnes - Returnerer veileder ident`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"veilederIdent":"V123"}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            sut.hentVeilederIdent(FNR_IN_TEST) shouldBe VEILEDER_IDENT_IN_TEST
        }

        @Test
        fun `HentVeilederIdent - Manglende tilgang - Kaster exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            val thrown = shouldThrow<UpstreamServiceException> {
                sut.hentVeilederIdent(FNR_IN_TEST)
            }

            thrown.statusCode shouldBe 403
            thrown.message shouldBe "Kall mot veilarboppfolging feilet under hent veileder (HTTP 403)"
        }

        @Test
        fun `HentVeilederIdent - Bruker finnes ikke - returnerer null`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT))

            sut.hentVeilederIdent(FNR_IN_TEST) shouldBe null
        }
    }

    @Nested
    inner class HentOppfolgingperioder {
        @Test
        fun `hentOppfolgingperioder - manglende tilgang - kaster exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            val thrown = shouldThrow<UpstreamServiceException> {
                sut.hentOppfolgingperioder(FNR_IN_TEST)
            }

            thrown.statusCode shouldBe 401
            thrown.message shouldBe "Kall mot veilarboppfolging feilet under hent oppfølgingsperioder (HTTP 401)"
        }

        @Test
        fun `hentOppfolgingperioder - tomt svar - returnerer tom liste`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

            sut.hentOppfolgingperioder(FNR_IN_TEST) shouldBe emptyList()
        }

        @Test
        fun `hentOppfolgingperioder - HTTP 500 - kaster retrybar exception`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            val thrown = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentOppfolgingperioder(FNR_IN_TEST)
            }

            thrown.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR.value()
        }

        @Test
        fun `hentOppfolgingperioder - tom HTTP 200-respons - kaster UpstreamServiceException med status`() {
            server
                .expect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

            val thrown = shouldThrow<UpstreamServiceException> {
                sut.hentOppfolgingperioder(FNR_IN_TEST)
            }

            thrown.statusCode shouldBe HttpStatus.OK.value()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `hentOppfolgingperioder - bruker finnes - returnerer oppfolgingsperidoer`(useEndDate: Boolean) {
            val expected = createOppfolgingPeriodeDto(useEndDate)

            server
                .expect(
                    requestTo("http://veilarboppfolging/veilarboppfolging/api/v3/oppfolging/hent-perioder"),
                ).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer veilarboppfolging-token"))
                .andExpect(content().json("""{"fnr":"$FNR_IN_TEST"}"""))
                .andRespond(
                    withSuccess(
                        objectMapper.writeValueAsString(listOf(expected)),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val oppfolgingsperioder = sut.hentOppfolgingperioder(FNR_IN_TEST)

            oppfolgingsperioder.size shouldBe 1
            assertSoftly(oppfolgingsperioder.first()) {
                id shouldBe expected.uuid
                startdato shouldBe nowAsLocalDateTime

                if (useEndDate) {
                    sluttdato shouldBe nowAsLocalDateTime.plusDays(1)
                } else {
                    sluttdato shouldBe null
                }
            }
        }
    }

    companion object {
        private const val VEILEDER_IDENT_IN_TEST = "V123"
        private const val FNR_IN_TEST = "123"

        private val nowAsZonedDateTimeUtc: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
        private val nowAsLocalDateTime: LocalDateTime =
            nowAsZonedDateTimeUtc
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()

        private fun createOppfolgingPeriodeDto(useEndDate: Boolean) = VeilarboppfolgingApi.OppfolgingPeriodeResponse(
            uuid = UUID.randomUUID(),
            startDato = nowAsZonedDateTimeUtc,
            sluttDato = if (useEndDate) nowAsZonedDateTimeUtc.plusDays(1) else null,
        )
    }
}
