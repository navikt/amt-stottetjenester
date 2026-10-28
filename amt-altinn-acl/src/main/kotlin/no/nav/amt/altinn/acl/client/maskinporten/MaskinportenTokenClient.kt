package no.nav.amt.altinn.acl.client.maskinporten

import no.nav.amt.altinn.acl.client.exception.MaskinportenTokenException
import no.nav.amt.altinn.acl.config.MASKINPORTEN_CLIENT_ID
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * Henter Maskinporten-token fra Nais sitt lokale token-endepunkt.
 * Nais håndterer klientautentisering, signering, caching og fornyelse.
 */
@Service
class MaskinportenTokenClient(
    private val maskinportenTokenRequest: MultiValueMap<String, String>,
    private val maskinportenTokenApi: MaskinportenTokenApi,
) {
    fun getAccessToken(): String = try {
        executeUpstreamCallWithRequiredBody(
            serviceName = MASKINPORTEN_CLIENT_ID,
            operation = "henting av maskinporten token",
        ) {
            maskinportenTokenApi.hentAccessToken(maskinportenTokenRequest)
        }.accessToken
    } catch (e: UpstreamServiceException) {
        when (val cause = e.cause) {
            is RestClientResponseException -> throw MaskinportenTokenException(
                statusCode = cause.statusCode.value(),
                errorCode = cause.safeOAuthErrorCode(),
            )

            else -> throw e
        }
    }

    private fun RestClientResponseException.safeOAuthErrorCode(): String = runCatching {
        getResponseBodyAs(OAuthErrorResponse::class.java)
    }.getOrNull()
        ?.error
        ?.takeIf { it in SAFE_OAUTH_ERROR_CODES }
        ?: UNKNOWN_ERROR_CODE

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    private data class OAuthErrorResponse(
        val error: String? = null,
    )

    companion object {
        private const val UNKNOWN_ERROR_CODE = "unknown"
        private val SAFE_OAUTH_ERROR_CODES = setOf(
            "invalid_request",
            "invalid_client",
            "invalid_grant",
            "unauthorized_client",
            "unsupported_grant_type",
            "invalid_scope",
            "invalid_target",
            "server_error",
        )
    }
}
