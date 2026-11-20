package no.nav.arrangor.client.person

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arrangor.client.RestClientTestBase
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
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@RestClientTest(PersonClient::class)
class PersonClientTest(
    private val sut: PersonClient,
) : RestClientTestBase("amt-person") {
    @Test
    fun `hentPersonalia - sender riktig request og parser respons`() {
        val id = UUID.randomUUID()
        val bearerToken = "Bearer amt-person-token"
        server
            .expect(requestTo("http://amt-person/api/arrangor-ansatt"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(content().json("""{"personident":"12345678910"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "$id",
                      "personident": "12345678910",
                      "fornavn": "Ola",
                      "mellomnavn": null,
                      "etternavn": "Nordmann"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        sut.hentPersonalia("12345678910").getOrThrow() shouldBe
            PersonApi.PersonResponse(id, "12345678910", "Ola", null, "Nordmann")
    }

    @Test
    fun `hentPersonalia - feilrespons returnerer failure`() {
        server
            .expect(requestTo("http://amt-person/api/arrangor-ansatt"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        sut.hentPersonalia("12345678910").isFailure shouldBe true
    }

    @Test
    fun `hentPersonalia - 404 returnerer failure med NoSuchElementException`() {
        server
            .expect(requestTo("http://amt-person/api/arrangor-ansatt"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        sut.hentPersonalia("12345678910").exceptionOrNull().shouldBeInstanceOf<NoSuchElementException>()
    }
}
