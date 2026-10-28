package no.nav.arrangor.utils

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException

/**
 * "pid" (personident) er NAV/ID-porten sitt claim for fødselsnummer/D-nummer på innlogget
 * sluttbruker. Brukes kun av ansatt-API-et, som er sikret med TokenX (se SecurityConfig) -
 * Azure AD-tokens (system-til-system) har ikke dette claimet.
 */
fun Jwt.personIdent(): String = getClaimAsString("pid")
    ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "PID is missing or is not a string")
