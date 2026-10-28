package no.nav.amt.altinn.acl.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import no.nav.amt.altinn.acl.jobs.AltinnUpdater
import no.nav.amt.altinn.acl.testutil.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class InternalControllerTest(
    private val mockMvc: MockMvc,
    @MockkBean private val altinnUpdater: AltinnUpdater,
) : IntegrationTest() {
    @BeforeEach
    fun setup() {
        coJustRun { altinnUpdater.update() }
    }

    /**
     * MockMvc bruker remoteAddr = "127.0.0.1" som standard, så requests regnes som interne.
     * Denne overstyrer adressen for å simulere et kall utenfra clusteret.
     */
    private fun MockHttpServletRequestDsl.fraEksternAdresse() {
        with { request ->
            request.remoteAddr = "10.0.0.1"
            request
        }
    }

    @Test
    fun `synkroniserAltinnRettigheter - intern adresse - returnerer 200 og starter synkronisering`() {
        val result = mockMvc
            .get(PATH)
            .andExpect { request { asyncStarted() } }
            .andReturn()

        mockMvc
            .perform(asyncDispatch(result))
            .andExpect(status().isOk)

        coVerify(exactly = 1) { altinnUpdater.update() }
    }

    @Test
    fun `synkroniserAltinnRettigheter - intern feil gir 500 uten feildetaljer`() {
        coEvery { altinnUpdater.update() } throws IllegalStateException("Intern feildetalj")

        val result = mockMvc
            .get(PATH)
            .andExpect { request { asyncStarted() } }
            .andReturn()

        mockMvc
            .perform(asyncDispatch(result))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(
                content().json(
                    """
                    {
                      "title": "Internal Server Error",
                      "status": 500,
                      "detail": "En uventet feil oppstod",
                      "instance": "$PATH"
                    }
                    """.trimIndent(),
                    JsonCompareMode.LENIENT,
                ),
            )

        coVerify(exactly = 1) { altinnUpdater.update() }
    }

    @Test
    fun `synkroniserAltinnRettigheter - ekstern adresse - returnerer 401 og synkroniserer ikke`() {
        mockMvc
            .get(PATH) { fraEksternAdresse() }
            .andExpect { status { isUnauthorized() } }

        coVerify(exactly = 0) { altinnUpdater.update() }
    }

    companion object {
        private const val PATH = "/internal/altinn/synkroniser"
    }
}
