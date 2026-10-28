package no.nav.arrangor.client.altinn

/**
 * Kastes når Altinn returnerer en rolle appen ikke kjenner til. Dette er en integrasjonsfeil hos
 * en nedstrøms tjeneste, ikke ugyldig input fra kalleren - se GlobalExceptionHandler for mapping
 * til 502 Bad Gateway (i motsetning til IllegalArgumentException, som gir 400 Bad Request).
 */
class UkjentAltinnRolleException(
    rolle: String,
) : RuntimeException("Ukjent tiltaksarrangørrolle") {
    val rolleForLogging: String =
        if (rolle.isNotBlank() && rolle.length <= 64 && rolle.all { it in 'A'..'Z' || it == '_' }) {
            rolle
        } else {
            "uventet format"
        }
}
