package no.nav.arrangor.configuration

import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import no.nav.arrangor.ControllerTestBase
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class SecurityConfigTest : ControllerTestBase() {
    @Test
    fun `error-dispatch til error uten token bevarer opprinnelig feilstatus`() {
        mockMvc
            .get("/error") {
                with { request ->
                    request.dispatcherType = DispatcherType.ERROR
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500)
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/internal/ansatte/republiser")
                    request
                }
            }.andExpect {
                status { isInternalServerError() }
            }
    }

    @Test
    fun `vanlig kall til error uten token krever autentisering`() {
        mockMvc
            .get("/error")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
