package no.nav.arrangor.internal

import no.nav.arrangor.ControllerTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get

class InternalAPITest : ControllerTestBase() {
    @Test
    fun `republiser ansatte - intern request - ok`() {
        mockMvc
            .get("/internal/ansatte/republiser") {
                with { request ->
                    request.remoteAddr = "127.0.0.1"
                    request
                }
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `republiser ansatte - ekstern request - unauthorized`() {
        mockMvc
            .get("/internal/ansatte/republiser") {
                with { request ->
                    request.remoteAddr = "10.0.0.1"
                    request
                }
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `republiser ansatte - autentisert ekstern request - forbidden`() {
        mockMvc
            .get("/internal/ansatte/republiser") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${getAzureAdToken()}")
                with { request ->
                    request.remoteAddr = "10.0.0.1"
                    request
                }
            }.andExpect {
                status { isForbidden() }
            }
    }
}
