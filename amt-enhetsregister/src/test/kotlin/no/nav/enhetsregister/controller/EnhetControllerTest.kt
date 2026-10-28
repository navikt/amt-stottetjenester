package no.nav.enhetsregister.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.enhetsregister.service.EnhetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.util.UUID

@WebMvcTest(controllers = [EnhetController::class])
class EnhetControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        @MockkBean private val enhetService: EnhetService,
    ) : ControllerTestBase() {
        @Test
        fun `hentEnhet returnerer 401 når kall ikke er autentisert`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders.get("/api/enhet/123456789"),
                    ).andReturn()
                    .response

            assertEquals(401, response.status)
            assertNotNull(response.getHeader("WWW-Authenticate"))
            assertEquals("", response.contentAsString)
        }

        @Test
        fun `hentEnhet returnerer 401 når audience er ugyldig`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/123456789")
                            .header("Authorization", "Bearer ${bearerToken(audience = "wrong-audience")}"),
                    ).andReturn()
                    .response

            assertEquals(401, response.status)
        }

        @Test
        fun `hentEnhet returnerer 401 når issuer er ugyldig`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/123456789")
                            .header("Authorization", "Bearer ${bearerToken(issuerId = "other-issuer")}"),
                    ).andReturn()
                    .response

            assertEquals(401, response.status)
        }

        @Test
        fun `hentEnhet returnerer 403 når tilgangsrollen mangler`() {
            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/123456789")
                            .header("Authorization", "Bearer ${bearerToken(roles = emptyList())}"),
                    ).andReturn()
                    .response

            assertEquals(403, response.status)
            assertEquals("", response.contentAsString)
        }

        @Test
        fun `hentEnhet returnerer 200 når enheten finnes`() {
            every {
                enhetService.hentEnhet(any())
            } returns
                EnhetService.EnhetMedOverordnetEnhet(
                    organisasjonsnummer = "123456789",
                    navn = "test",
                    overordnetEnhetOrganisasjonsnummer = null,
                    overordnetEnhetNavn = null,
                )

            val token = bearerToken()

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/123456789")
                            .header("Authorization", "Bearer $token"),
                    ).andReturn()
                    .response

            verify(exactly = 1) { enhetService.hentEnhet("123456789") }

            assertEquals(200, response.status)
        }

        @Test
        fun `hentEnhet returnerer 404 når enheten ikke finnes`() {
            every { enhetService.hentEnhet(any()) } returns null

            val token = bearerToken()

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/999222111")
                            .header("Authorization", "Bearer $token"),
                    ).andReturn()
                    .response

            assertEquals(404, response.status)
        }

        @Test
        fun `hentEnhet returnerer en skjermet 500-feil når tjenesten feiler uventet`() {
            val exceptionMessage = "distinctive internal failure"
            every { enhetService.hentEnhet(any()) } throws IllegalStateException(exceptionMessage)

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/123456789")
                            .header("Authorization", "Bearer ${bearerToken()}"),
                    ).andReturn()
                    .response

            assertEquals(500, response.status)
            assertTrue(Regex(""""detail"\s*:\s*"En uventet feil oppstod"""").containsMatchIn(response.contentAsString))
            val errorId =
                Regex(""""errorId"\s*:\s*"([^"]+)"""")
                    .find(response.contentAsString)
                    ?.groupValues
                    ?.get(1)
            UUID.fromString(requireNotNull(errorId))
            assertFalse(response.contentAsString.contains(exceptionMessage))
        }

        @Test
        fun `hentEnhet returnerer 400 hvis orgnummer har ugyldig format`() {
            every { enhetService.hentEnhet(any()) } returns null

            val token = bearerToken()

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/api/enhet/999222111test")
                            .header("Authorization", "Bearer $token"),
                    ).andReturn()
                    .response

            assertEquals(400, response.status)
        }
    }
