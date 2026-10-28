package no.nav.amt.altinn.acl.config

import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import no.nav.amt.altinn.acl.testutil.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class SecurityConfigTest(
    private val mockMvc: MockMvc,
) : IntegrationTest() {
    @Test
    fun `error-dispatch uten token beholder opprinnelig feilstatus`() {
        mockMvc
            .get("/error") {
                with { request ->
                    // Servlet-containeren bruker ERROR ved intern redispatch etter at et endepunkt har feilet.
                    request.dispatcherType = DispatcherType.ERROR

                    // Spring Boots /error-endepunkt leser disse attributtene fra den opprinnelige forespørselen.
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500)
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/internal/altinn/synkroniser")
                    request
                }
            }.andExpect {
                // Uten ERROR-regelen i SecurityConfig ville anyRequest krevd token og svart 401.
                status { isInternalServerError() }
                content {
                    json(
                        """
                        {
                          "status": 500,
                          "error": "Internal Server Error",
                          "path": "/internal/altinn/synkroniser"
                        }
                        """.trimIndent(),
                        JsonCompareMode.LENIENT,
                    )
                }
            }
    }
}
