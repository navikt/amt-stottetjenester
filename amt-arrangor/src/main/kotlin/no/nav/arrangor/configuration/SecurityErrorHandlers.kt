package no.nav.arrangor.configuration

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Sørger for at 401/403 fra Spring Security-filterkjeden (før DispatcherServlet)
 * returnerer samme ProblemDetail-kontrakt som resten av APIet.
 */
@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    private val log = LoggerFactory.getLogger(javaClass)

    // Delegerer til Spring sin egen entrypoint for å sette WWW-Authenticate-headeren og status
    // per RFC 6750 (error/error_description/scope) - se kommentar i commence() under.
    private val bearerTokenEntryPoint = BearerTokenAuthenticationEntryPoint()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        // Denne kalles både når det mangler Authorization-header (ExceptionTranslationFilter) og når
        // selve JWT-en er ugyldig (BearerTokenAuthenticationFilter) - se SecurityConfig for hvor begge
        // kobles til denne samme beanen.
        // Kun unntakstypen logges; meldingen kan inneholde token-detaljer (issuer/audience o.l.).
        log.warn(
            "Uautentisert forespørsel: method={}, årsak={}",
            request.method,
            authException.javaClass.simpleName,
        )
        // Behold Spring sin statuskode, men erstatt headeren etterpå fordi OAuth-feilbeskrivelsen
        // kan inneholde interne valideringsdetaljer som ikke skal sendes til kalleren.
        bearerTokenEntryPoint.commence(
            request,
            response,
            authException,
        )
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.writeErrorResponse(
            objectMapper = objectMapper,
            status = HttpStatus.valueOf(response.status),
        )
    }
}

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        // Samme begrunnelse som i RestAuthenticationEntryPoint - ikke lekk interne detaljer
        // om hvorfor tilgang ble avvist.
        log.warn(
            "Ikke tilgang til ressurs: method={}, årsak={}",
            request.method,
            accessDeniedException.javaClass.simpleName,
        )
        response.writeErrorResponse(
            objectMapper = objectMapper,
            status = HttpStatus.FORBIDDEN,
        )
    }
}

// Skriver direkte til outputStream fordi vi er i Spring Security-filterkjeden, før DispatcherServlet
// og HttpMessageConverter-maskineriet er i bildet - se klassekommentaren øverst i filen.
// Bruker samme ProblemDetail-format og feiltekster som controller advice.
private fun HttpServletResponse.writeErrorResponse(
    objectMapper: ObjectMapper,
    status: HttpStatus,
) {
    this.status = status.value()
    contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    objectMapper.writeValue(
        outputStream,
        createProblemDetail(
            status = status,
            detail = clientErrorDetail(status),
        ),
    )
}
