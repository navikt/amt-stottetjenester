package no.nav.arrangor.ansatt

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import no.nav.arrangor.ControllerTestBase
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.client.altinn.UkjentAltinnRolleException
import no.nav.arrangor.configuration.GlobalExceptionHandler
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class AnsattServiceUserAPITest(
    private val ansattRepository: AnsattRepository,
) : ControllerTestBase() {
    private val exceptionHandlerLogger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachLogAppender() {
        logAppender.list.clear()
        logAppender.start()
        exceptionHandlerLogger.addAppender(logAppender)
    }

    @AfterEach
    fun detachLogAppender() {
        exceptionHandlerLogger.detachAppender(logAppender)
        logAppender.stop()
    }

    @Test
    fun `getAnsatt - ikke gyldig token - unauthorized`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody("12345678910")),
        )

        response.code shouldBe 401
        response.contentType shouldBe "application/problem+json"
        val error = objectMapper.readTree(response.body.string())
        error["status"].asInt() shouldBe 401
        error["title"].asString() shouldBe "Unauthorized"
        error["detail"].asString() shouldBe "Ikke autentisert"
    }

    @Test
    fun `getAnsatt - Azure AD-token uten access_as_application-rolle - forbidden`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody("12345678910")),
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken(claims = emptyMap())}",
            ),
        )

        response.code shouldBe 403
        response.contentType shouldBe "application/problem+json"
        val error = objectMapper.readTree(response.body.string())
        error["status"].asInt() shouldBe 403
        error["title"].asString() shouldBe "Forbidden"
        error["detail"].asString() shouldBe "Ikke tilgang"
    }

    @Test
    fun `getAnsatt - TokenX-token - unauthorized`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody("12345678910")),
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken("12345678910")}",
            ),
        )

        response.code shouldBe 401
    }

    @Test
    fun `getAnsatt - Azure AD-token med feil audience - unauthorized`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody("12345678910")),
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken(audience = "wrong-audience")}",
            ),
        )

        response.code shouldBe 401
        response.contentType shouldBe "application/problem+json"
        val error = objectMapper.readTree(response.body.string())
        error["status"].asInt() shouldBe 401
        error["title"].asString() shouldBe "Unauthorized"
    }

    @Test
    fun `getAnsatt - utløpt Azure AD-token - unauthorized`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody("12345678910")),
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken(expiry = -1)}",
            ),
        )

        response.code shouldBe 401
    }

    @Test
    fun `fjernTilgangerHosArrangor - no token - unauthorized`() {
        sendRequest("DELETE", "/api/service/ansatt/tilganger", "")
            .also { it.code shouldBe 401 }
    }

    @Test
    fun `fjernTilgangerHosArrangor - tokenx token - unauthorized`() {
        sendRequest(
            method = "DELETE",
            path = "/api/service/ansatt/tilganger",
            body = "",
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = "foobar")}"),
        ).also { it.code shouldBe 401 }
    }

    @Test
    fun `getAnsatt - autentisert - returnerer ansatt`() {
        val arrangorOne = testDatabase.insertArrangor()
        val arrangorTwo = testDatabase.insertArrangor()
        val personident = "12345678910"
        val personId = UUID.randomUUID()
        mockAltinnRoller(
            personident,
            mapOf(
                arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR),
                arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
            ),
        )
        mockPerson(personident, personId, "Test", null, "Testersen")

        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody(personident)),
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 200
        val ansatt = objectMapper.readValue<Ansatt>(response.body.string())
        ansatt.personalia.personId shouldBe personId
        ansatt.arrangorer.size shouldBe 2
        ansattRepository.get(personident) shouldNotBe null
    }

    @Test
    fun `getAnsatt - autentisert, ansatt finnes ikke og har ingen roller - returnerer 404 og oppretter ikke ansatt`() {
        val personident = "12345678910"
        val personId = UUID.randomUUID()
        mockAltinnRoller(
            personident,
            emptyMap(),
        )
        mockPerson(personident, personId, "Test", null, "Testersen")

        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody(personident)),
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 404
        response.contentType shouldBe "application/problem+json"
        val error = objectMapper.readTree(response.body.string())
        error["detail"].asString() shouldBe "Ressursen finnes ikke"
        ansattRepository.get(personident) shouldBe null
    }

    @Test
    fun `getAnsatt - autentisert, personident har feil format - returnerer 400 og oppretter ikke ansatt`() {
        val personident = "123456789101"
        val personId = UUID.randomUUID()
        mockAltinnRoller(
            personident,
            emptyMap(),
        )
        mockPerson(personident, personId, "Test", null, "Testersen")

        val response =
            sendRequest(
                method = "POST",
                path = "/api/service/ansatt",
                body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody(personident)),
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
            )

        response.code shouldBe 400
        response.contentType shouldBe "application/problem+json"
        val error = objectMapper.readTree(response.body.string())
        error["detail"].asString() shouldBe "Forespørselen inneholder ugyldige data"
        ansattRepository.get(personident) shouldBe null
    }

    @Test
    fun `getAnsatt - ukjent Altinn-rolle returnerer 502 uten interne detaljer`() {
        val personident = "12345678910"
        every { altinnAclClient.hentRoller(personident) } returns
            Result.failure(UkjentAltinnRolleException("UKJENT_ROLLE"))

        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody(personident)),
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 502
        response.contentType shouldBe "application/problem+json"
        val body = response.body.string()
        body.shouldNotContain("UKJENT_ROLLE")
        val error = objectMapper.readTree(body)
        error["status"].asInt() shouldBe 502
        error["title"].asString() shouldBe "Bad Gateway"
        error["detail"].asString() shouldBe "En uventet feil oppstod"
        error["errorId"].asString().isNotBlank() shouldBe true

        val logEvent = logAppender.list.single()
        logEvent.throwableProxy shouldBe null
        logEvent.formattedMessage shouldContain "altinnRolle=UKJENT_ROLLE"
        logEvent.formattedMessage shouldNotContain "hemmelig-rolle"
    }

    @Test
    fun `getAnsatt - uventet feil returnerer 500 uten interne detaljer`() {
        val personident = "12345678910"
        every { altinnAclClient.hentRoller(personident) } returns
            Result.failure(IllegalStateException("intern-feilmelding"))

        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = objectMapper.writeValueAsString(AnsattServiceUserAPI.AnsattRequestBody(personident)),
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 500
        response.contentType shouldBe "application/problem+json"
        val body = response.body.string()
        body.shouldNotContain("intern-feilmelding")
        val error = objectMapper.readTree(body)
        error["detail"].asString() shouldBe "En uventet feil oppstod"
        error["errorId"].asString().isNotBlank() shouldBe true

        val logEvent = logAppender.list.single()
        logEvent.throwableProxy shouldBe null
        logEvent.formattedMessage shouldContain "java.lang.IllegalStateException"
        logEvent.formattedMessage shouldNotContain "intern-feilmelding"
    }

    @Test
    fun `getAnsatt - ugyldig JSON returnerer sanert 400 fra Spring MVC`() {
        val response = sendRequest(
            method = "POST",
            path = "/api/service/ansatt",
            body = """{"personident": """,
            headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
        )

        response.code shouldBe 400
        response.contentType shouldBe "application/problem+json"
        objectMapper.readTree(response.body.string())["detail"].asString() shouldBe
            "Forespørselen inneholder ugyldige data"
    }

    @Test
    fun `getAnsatt (id) - ikke gyldig token - unauthorized`() {
        val response =
            sendRequest(
                method = "GET",
                path = "/api/service/ansatt/${UUID.randomUUID()}",
            )

        response.code shouldBe 401
    }

    @Test
    fun `getAnsatt (id) - autentisert - returnerer ansatt`() {
        val arrangorOne = testDatabase.insertArrangor()
        val personident = "12345678910"
        val personId = UUID.randomUUID()
        val ansattDbo =
            testDatabase.insertAnsatt(
                personident = personident,
                personId = personId,
                fornavn = "Test",
                mellomnavn = null,
                etternavn = "Testersen",
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
                            veileder = emptyList(),
                        ),
                    ),
            )
        mockPerson(personident, personId, "Test", null, "Testersen")

        val response =
            sendRequest(
                method = "GET",
                path = "/api/service/ansatt/${ansattDbo.id}",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
            )

        response.code shouldBe 200
        val ansatt = objectMapper.readValue<Ansatt>(response.body.string())
        ansatt.personalia.personId shouldBe personId
        ansatt.arrangorer.size shouldBe 1
        ansattRepository.get(personident) shouldNotBe null
    }

    @Test
    fun `getAnsatt (id) - autentisert, ansatt finnes ikke - returnerer 404 og oppretter ikke ansatt`() {
        val ansattId = UUID.randomUUID()

        val response =
            sendRequest(
                method = "GET",
                path = "/api/service/ansatt/$ansattId",
                headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getAzureAdToken()}"),
            )

        response.code shouldBe 404
        ansattRepository.get(ansattId) shouldBe null
    }
}
