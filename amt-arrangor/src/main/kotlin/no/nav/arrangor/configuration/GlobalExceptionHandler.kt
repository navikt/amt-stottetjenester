package no.nav.arrangor.configuration

import no.nav.arrangor.client.altinn.UkjentAltinnRolleException
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
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(): ProblemDetail = createProblemDetail(
        status = HttpStatus.NOT_FOUND,
        detail = "Ressursen finnes ikke",
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(): ProblemDetail = createProblemDetail(
        status = HttpStatus.BAD_REQUEST,
        detail = "Forespørselen inneholder ugyldige data",
    )

    @ExceptionHandler(UkjentAltinnRolleException::class)
    fun handleUnknownAltinnRole(exception: UkjentAltinnRolleException): ProblemDetail = serverError(
        exception = exception,
        status = HttpStatus.BAD_GATEWAY,
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception): ProblemDetail = serverError(
        exception = exception,
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
            serverError(
                exception = ex,
                status = statusCode,
            )
        } else {
            createProblemDetail(
                status = statusCode,
                detail = clientErrorDetail(statusCode),
            )
        }

        return super.handleExceptionInternal(
            ex,
            sanitizedBody,
            headers,
            statusCode,
            request,
        )
    }

    private fun serverError(
        exception: Exception,
        status: HttpStatusCode,
    ): ProblemDetail {
        val errorId = MDC.get("trace_id") ?: UUID.randomUUID().toString()
        log.error(
            "Uventet feil under behandling av forespørsel, errorId={}, status={}, altinnRolle={}, sanitizedStackTrace={}",
            errorId,
            status.value(),
            (exception as? UkjentAltinnRolleException)?.rolleForLogging,
            sanitizedStackTrace(exception),
        )
        return createProblemDetail(
            status = status,
            detail = "En uventet feil oppstod",
        ).apply {
            setProperty(
                "errorId",
                errorId,
            )
        }
    }

    private fun sanitizedStackTrace(exception: Exception): String = generateSequence<Throwable>(exception) { it.cause }
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

    companion object {
        private const val MAX_CAUSE_DEPTH = 10
    }
}

internal fun createProblemDetail(
    status: HttpStatusCode,
    detail: String,
): ProblemDetail = ProblemDetail
    .forStatusAndDetail(
        status,
        detail,
    ).apply {
        title = HttpStatus.resolve(status.value())?.reasonPhrase ?: "HTTP-feil"
    }

internal fun clientErrorDetail(status: HttpStatusCode): String = when (status.value()) {
    HttpStatus.BAD_REQUEST.value() -> "Forespørselen inneholder ugyldige data"
    HttpStatus.UNAUTHORIZED.value() -> "Ikke autentisert"
    HttpStatus.FORBIDDEN.value() -> "Ikke tilgang"
    HttpStatus.NOT_FOUND.value() -> "Ressursen finnes ikke"
    else -> "Forespørselen kunne ikke behandles"
}
