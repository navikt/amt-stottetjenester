package no.nav.amt.altinn.acl.config

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.UUID

@RestControllerAdvice
class ExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(): ProblemDetail = problemDetail(
        status = HttpStatus.NOT_FOUND,
        detail = "Ressursen finnes ikke",
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(): ProblemDetail = problemDetail(
        status = HttpStatus.BAD_REQUEST,
        detail = "Forespørselen inneholder ugyldige data",
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(e: Exception): ProblemDetail = internalServerError(
        e = e,
        status = HttpStatus.INTERNAL_SERVER_ERROR,
    )

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val sanitizedBody = if (statusCode.is5xxServerError) {
            internalServerError(
                e = ex,
                status = statusCode,
            )
        } else {
            problemDetail(
                status = statusCode,
                detail = when (HttpStatus.resolve(statusCode.value())) {
                    HttpStatus.BAD_REQUEST -> "Forespørselen inneholder ugyldige data"
                    HttpStatus.NOT_FOUND -> "Ressursen finnes ikke"
                    else -> "Forespørselen kunne ikke behandles"
                },
            )
        }

        return super.handleExceptionInternal(ex, sanitizedBody, headers, statusCode, request)
    }

    private fun internalServerError(
        e: Exception,
        status: HttpStatusCode,
    ): ProblemDetail {
        val errorId = MDC.get("trace_id") ?: UUID.randomUUID().toString()

        log.error(
            "Uventet feil under behandling av forespørsel, errorId={}, sanitizedStackTrace={}",
            errorId,
            sanitizedStackTrace(e),
        )

        return problemDetail(
            status = status,
            detail = "En uventet feil oppstod",
        ).apply {
            setProperty("errorId", errorId)
        }
    }

    private fun sanitizedStackTrace(e: Exception): String = generateSequence<Throwable>(e) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .joinToString(separator = "\nCaused by: ") { throwable ->
            buildString {
                append(throwable.javaClass.name)
                throwable.stackTrace.forEach { stackTraceElement ->
                    append("\n\tat ")
                    append(stackTraceElement)
                }
            }
        }

    private fun problemDetail(
        status: HttpStatusCode,
        detail: String,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
        title = HttpStatus.resolve(status.value())?.reasonPhrase ?: "HTTP-feil"
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 10
    }
}
