package no.nav.amt.person.service.integration.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import no.nav.amt.lib.spring.boot.security.InternalAuthorizationManager
import no.nav.amt.person.service.config.SecurityConfig
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.internal.InternalController
import no.nav.amt.person.service.internal.InternalService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(InternalController::class, SecurityConfig::class, InternalAuthorizationManager::class)
@ActiveProfiles("integration")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class InternalControllerTest(
    private val mockMvc: MockMvc,
    @MockkBean private val internalService: InternalService,
) {
    @BeforeEach
    fun setup() {
        justRun { internalService.oppdaterPersonidenter(any()) }
        justRun { internalService.oppdaterNavn(any()) }
        justRun { internalService.republiserNavBrukere(any(), any()) }
        justRun { internalService.oppdaterAdresseOgRepubliserNavBrukere(any(), any(), any()) }
        justRun { internalService.oppdaterAdresseOgRepubliserNavBruker(any()) }
        justRun { internalService.oppdaterInnsatsOgRepubliserNavBrukere(any(), any(), any(), any()) }
        justRun { internalService.oppdaterInnsatsOgRepubliserNavBruker(any()) }
        justRun { internalService.publiserNavBruker(any()) }
        justRun { internalService.republiserArrangorAnsatte(any(), any()) }
        justRun { internalService.republiserNavAnsatte() }
        justRun { internalService.oppdaterNavAnsatte() }
        justRun { internalService.oppdaterNavEnheter() }
        justRun { internalService.synkroniserKrr(any(), any()) }
        justRun { internalService.synkroniserKrrForPerson(any()) }
        justRun { internalService.oppdaterManglendeKontaktinfo(any()) }
        justRun { internalService.republiserNavBrukereMedNyIdent() }
    }

    // MockMvc defaults to remoteAddr = "127.0.0.1", so requests are treated as internal by default.
    private fun MockHttpServletRequestDsl.fraEksternAdresse() {
        with { request ->
            request.remoteAddr = "10.0.0.1"
            request
        }
    }

    @Nested
    inner class Autentisering {
        @Test
        fun `skal returnere 401 for alle GET-endepunkter som ikke er interne`() {
            val endepunkter = listOf(
                "/internal/nav-brukere/republiser",
                "/internal/nav-brukere/oppdater-adr-republiser",
                "/internal/nav-bruker/oppdater-adr-republiser/${UUID.randomUUID()}",
                "/internal/nav-brukere/oppdater-innsats-republiser",
                "/internal/nav-bruker/oppdater-innsats-republiser/${UUID.randomUUID()}",
                "/internal/nav-brukere/republiser/${UUID.randomUUID()}",
                "/internal/arrangor-ansatte/republiser",
                "/internal/nav-ansatte/republiser",
                "/internal/nav-ansatte/oppdater",
                "/internal/nav-enhet/oppdater",
                "/internal/nav-brukere/synkroniser-krr",
                "/internal/nav-brukere/oppdater-manglende-kontaktinfo",
                "/internal/nav-brukere/republiser-ny-ident",
            )

            endepunkter.forEach { endpoint ->
                mockMvc
                    .get(endpoint) {
                        fraEksternAdresse()
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andExpect { status { isUnauthorized() } }
            }
        }
    }

    @Nested
    inner class OppdaterPersonidenter {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .post("/internal/person/identer")
                .andExpect { status { isOk() } }

            verify(timeout = 5_000) { internalService.oppdaterPersonidenter(0) }
        }

        @Test
        fun `skal avvise request fra ekstern adresse`() {
            mockMvc
                .post("/internal/person/identer") {
                    fraEksternAdresse()
                }.andExpect { status { isUnauthorized() } }

            verify(exactly = 0) { internalService.oppdaterPersonidenter(any()) }
        }

        @Test
        fun `skal bruke offset-parameter`() {
            mockMvc
                .post("/internal/person/identer") {
                    param("offset", "100")
                }.andExpect { status { isOk() } }

            verify(timeout = 5_000) { internalService.oppdaterPersonidenter(100) }
        }
    }

    @Nested
    inner class OppdaterNavn {
        @Test
        fun `skal returnere 200 og oppdatere navn for intern request`() {
            mockMvc
                .get("/internal/person/navn/${UUID.randomUUID()}")
                .andExpect { status { isOk() } }
        }

        @Test
        fun `skal avvise request fra ekstern adresse`() {
            mockMvc
                .get("/internal/person/navn/${UUID.randomUUID()}") {
                    fraEksternAdresse()
                }.andExpect { status { isUnauthorized() } }

            verify(exactly = 0) { internalService.oppdaterNavn(any()) }
        }
    }

    @Nested
    inner class RepubliserNavBrukere {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/republiser")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class OppdaterOgRepubliserNavBrukere {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/oppdater-adr-republiser")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class OppdaterAdresseOgRepubliserNavBruker {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-bruker/oppdater-adr-republiser/${UUID.randomUUID()}")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class OppdaterInnsatsOgRepubliser {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/oppdater-innsats-republiser")
                .andExpect { status { isOk() } }
        }

        @Test
        fun `skal returnere 200 for enkelt Nav-bruker`() {
            mockMvc
                .get("/internal/nav-bruker/oppdater-innsats-republiser/${UUID.randomUUID()}")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class RepubliserNavBruker {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/republiser/${UUID.randomUUID()}")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class RepubliserArrangorAnsatte {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/arrangor-ansatte/republiser")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class RepubliserNavAnsatte {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-ansatte/republiser")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class OppdaterNavAnsatte {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-ansatte/oppdater")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class OppdaterNavEnheter {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-enhet/oppdater")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class SynkroniserKrr {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/synkroniser-krr")
                .andExpect { status { isOk() } }
        }

        @Test
        fun `skal returnere 401 for ekstern request`() {
            mockMvc
                .post("/internal/nav-brukere/synkroniser-krr") {
                    fraEksternAdresse()
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": "${TestData.randomIdent()}"}"""
                }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `skal synkronisere kontaktinfo for enkelt Nav-bruker`() {
            mockMvc
                .post("/internal/nav-brukere/synkroniser-krr") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": "${TestData.randomIdent()}"}"""
                }.andExpect { status { isOk() } }
        }

        @Test
        fun `skal returnere 500 når Nav-bruker ikke finnes`() {
            every { internalService.synkroniserKrrForPerson(any()) } throws
                IllegalArgumentException("Sensitiv feilmelding")

            mockMvc
                .post("/internal/nav-brukere/synkroniser-krr") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": "${TestData.randomIdent()}"}"""
                }.andExpect { status { isInternalServerError() } }
                .andExpect {
                    content {
                        contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                        string(not(containsString("Sensitiv feilmelding")))
                    }
                    jsonPath("$.detail") { value("En uventet feil oppstod") }
                    jsonPath("$.errorId") { isNotEmpty() }
                    jsonPath("$.stacktrace") { doesNotExist() }
                }
        }

        @Test
        fun `skal returnere 400 uten å eksponere ugyldig request`() {
            mockMvc
                .post("/internal/nav-brukere/synkroniser-krr") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": """
                }.andExpect { status { isBadRequest() } }
                .andExpect {
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                    jsonPath("$.detail") { value("Forespørselen inneholder ugyldige data") }
                    jsonPath("$.errorId") { doesNotExist() }
                }
        }

        @Test
        fun `skal returnere 404 uten intern feilmelding`() {
            every { internalService.synkroniserKrrForPerson(any()) } throws
                NoSuchElementException("Sensitiv feilmelding")

            mockMvc
                .post("/internal/nav-brukere/synkroniser-krr") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"personident": "${TestData.randomIdent()}"}"""
                }.andExpect { status { isNotFound() } }
                .andExpect {
                    jsonPath("$.detail") { value("Ressursen finnes ikke") }
                    jsonPath("$.errorId") { doesNotExist() }
                }
        }
    }

    @Nested
    inner class OppdaterManglendeKontaktinfo {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/oppdater-manglende-kontaktinfo")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class RepubliserNavBrukereMedNyIdent {
        @Test
        fun `skal returnere 200 for intern request`() {
            mockMvc
                .get("/internal/nav-brukere/republiser-ny-ident")
                .andExpect { status { isOk() } }
        }
    }
}
