package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.ControllerTestBase
import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class ArrangorServiceUserAPITest : ControllerTestBase() {
    @Test
    fun `getArrangor - ikke gyldig token - unauthorized`() {
        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/organisasjonsnummer/123456789",
        )

        response.code shouldBe 401
    }

    @Test
    fun `getArrangor - autentisert, orgnummer har feil format - returnerer 400`() {
        val orgnummer = "12345678910"

        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 400
    }

    @Test
    fun `getArrangor - autentisert, arrangor har ikke overordnet arrangor - returnerer arrangor`() {
        val orgnummer = "923456789"
        testDatabase.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = null)

        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 200
        val arrangor = objectMapper.readValue<ArrangorMedOverordnetArrangor>(response.body.string())
        arrangor.organisasjonsnummer shouldBe orgnummer
        arrangor.navn shouldBe "Navn"
        arrangor.overordnetArrangor shouldBe null
    }

    @Test
    fun `getArrangor - autentisert, orgnummer med whitespace - returnerer arrangor`() {
        val orgnummer = "923456789"
        testDatabase.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = null)

        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/organisasjonsnummer/%20$orgnummer%20",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 200
        val arrangor = objectMapper.readValue<ArrangorMedOverordnetArrangor>(response.body.string())
        arrangor.organisasjonsnummer shouldBe orgnummer
        arrangor.navn shouldBe "Navn"
        arrangor.overordnetArrangor shouldBe null
    }

    @Test
    fun `getArrangor - autentisert, arrangor har overordnet arrangor - returnerer arrangor`() {
        val orgnummerOverordnetArrangor = "987654321"
        val overordnetArrangor = testDatabase.insertArrangor(
            navn = "Overordnet",
            organisasjonsnummer = orgnummerOverordnetArrangor,
            overordnetArrangorId = null,
        )
        val orgnummer = "923456789"
        testDatabase.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = overordnetArrangor.id)

        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 200
        val arrangor = objectMapper.readValue<ArrangorMedOverordnetArrangor>(response.body.string())
        arrangor.organisasjonsnummer shouldBe orgnummer
        arrangor.navn shouldBe "Navn"
        arrangor.overordnetArrangor?.id shouldBe overordnetArrangor.id
        arrangor.overordnetArrangor?.navn shouldBe "Overordnet"
        arrangor.overordnetArrangor?.organisasjonsnummer shouldBe orgnummerOverordnetArrangor
    }

    @Test
    fun `getArrangor (id) - ikke gyldig token - unauthorized`() {
        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/${UUID.randomUUID()}",
        )

        response.code shouldBe 401
    }

    @Test
    fun `getArrangor (id) - autentisert, arrangor finnes ikke - returnerer 404`() {
        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/${UUID.randomUUID()}",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 404
    }

    @Test
    fun `getArrangor (id) - autentisert, arrangor har overordnet arrangor - returnerer arrangor`() {
        val orgnummerOverordnetArrangor = "987654321"
        val overordnetArrangor = testDatabase.insertArrangor(
            navn = "Overordnet",
            organisasjonsnummer = orgnummerOverordnetArrangor,
            overordnetArrangorId = null,
        )
        val orgnummer = "923456789"
        val arrangor =
            testDatabase.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = overordnetArrangor.id)

        val response = sendRequest(
            method = "GET",
            path = "/api/service/arrangor/${arrangor.id}",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 200
        val arrangorResponse = objectMapper.readValue<ArrangorMedOverordnetArrangor>(response.body.string())

        arrangorResponse.organisasjonsnummer shouldBe orgnummer
        arrangorResponse.navn shouldBe "Navn"
        arrangorResponse.overordnetArrangor?.id shouldBe overordnetArrangor.id
        arrangorResponse.overordnetArrangor?.navn shouldBe "Overordnet"
        arrangorResponse.overordnetArrangor?.organisasjonsnummer shouldBe orgnummerOverordnetArrangor
    }
}
