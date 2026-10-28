package no.nav.enhetsregister.config

import no.nav.common.log.MDCConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import java.util.UUID

class GlobalExceptionHandlerTest {
    private val exceptionHandler = GlobalExceptionHandler()

    @Test
    fun `generisk feil gir skjermet 500-respons med generert error ID`() {
        val exceptionMessage = "distinctive internal failure"

        val response = exceptionHandler.handleUnexpectedException(IllegalStateException(exceptionMessage))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.status)
        assertEquals("En uventet feil oppstod", response.detail)

        val errorId = response.properties?.get("errorId") as? String
        UUID.fromString(requireNotNull(errorId))
    }

    @Test
    fun `bruker kall-ID fra MDC som error ID`() {
        val callId = "request-call-id"
        MDC.put(MDCConstants.MDC_CALL_ID, callId)

        try {
            val response = exceptionHandler.handleUnexpectedException(IllegalStateException("internal failure"))

            assertEquals(callId, response.properties?.get("errorId"))
        } finally {
            MDC.remove(MDCConstants.MDC_CALL_ID)
        }
    }
}
