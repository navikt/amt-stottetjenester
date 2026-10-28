package no.nav.amt.person.service.config

import no.nav.common.log.MDCConstants
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(): ProblemDetail = problemDetail(HttpStatus.NOT_FOUND)

    @ExceptionHandler(AuthenticationException::class)
    fun handleUnauthorized(): ProblemDetail = problemDetail(HttpStatus.UNAUTHORIZED)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(): ProblemDetail = problemDetail(HttpStatus.FORBIDDEN)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ProblemDetail = internalServerError(ex, HttpStatus.INTERNAL_SERVER_ERROR)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val sanitizedBody = if (statusCode.is5xxServerError) {
            internalServerError(ex, statusCode)
        } else {
            problemDetail(statusCode)
        }

        return super.handleExceptionInternal(ex, sanitizedBody, headers, statusCode, request)
    }

    private fun internalServerError(
        ex: Exception,
        status: HttpStatusCode,
    ): ProblemDetail {
        val errorId = MDC.get(MDCConstants.MDC_CALL_ID) ?: UUID.randomUUID().toString()
        log.error(
            "Uventet feil under behandling av forespørsel, errorId={}, sanitizedStackTrace={}",
            errorId,
            sanitizedStackTrace(ex),
        )
        return problemDetail(status).apply { setProperty("errorId", errorId) }
    }

    private fun sanitizedStackTrace(ex: Exception): String = generateSequence<Throwable>(ex) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .joinToString(separator = "\nCaused by: ") { throwable ->
            buildString {
                append(throwable.javaClass.name)
                throwable.stackTrace.forEach { frame ->
                    append("\n\tat ")
                    append(frame)
                }
            }
        }

    private fun problemDetail(status: HttpStatusCode): ProblemDetail {
        val detail = when {
            status.is5xxServerError -> "En uventet feil oppstod"
            status.value() == HttpStatus.BAD_REQUEST.value() -> "Forespørselen inneholder ugyldige data"
            status.value() == HttpStatus.NOT_FOUND.value() -> "Ressursen finnes ikke"
            else -> "Forespørselen kunne ikke behandles"
        }
        return ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = HttpStatus.resolve(status.value())?.reasonPhrase ?: "HTTP-feil"
        }
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 10
    }
}
