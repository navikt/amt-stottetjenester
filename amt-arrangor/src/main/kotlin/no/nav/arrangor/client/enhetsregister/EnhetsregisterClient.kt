package no.nav.arrangor.client.enhetsregister

import no.nav.arrangor.utils.Orgnummer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class EnhetsregisterClient(
    @Value($$"${AMT_ENHETSREGISTER_ALLOWED_HOSTS}") private val allowedHosts: Set<String>,
    @Value($$"${spring.http.serviceclient.amt-enhetsregister.base-url}") private val baseUrl: String,
    private val enhetsregisterApi: EnhetsregisterApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val validatedBaseUrl = validateBaseUrl(baseUrl)

    fun hentVirksomhet(orgNr: String): Result<Virksomhet> {
        if (!Orgnummer.erGyldig(orgNr)) {
            return Result.failure(IllegalArgumentException("Ugyldig organisasjonsnummer"))
        }

        return runCatching {
            enhetsregisterApi
                .hentVirksomhet(orgNr)
                .also { log.info("hentVirksomhet $orgNr hentet fra ${validatedBaseUrl.host}") }
        }
    }

    private fun validateBaseUrl(url: String): URI {
        require(allowedHosts.isNotEmpty()) {
            "allowedHosts for Enhetsregister må være konfigurert (baseUrl=$url)"
        }

        val parsed = runCatching { URI(url) }
            .getOrElse { throw IllegalArgumentException("Ugyldig baseUrl for Enhetsregister: baseUrl=$url", it) }
        require(parsed.scheme in setOf("http", "https") && parsed.host != null) {
            "Ugyldig baseUrl for Enhetsregister: baseUrl=$url"
        }

        val host = parsed.host
        val hostAllowed = allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }

        if (!hostAllowed) {
            throw IllegalArgumentException(
                "Ugyldig baseUrl for Enhetsregister: baseUrl=$url, host=$host, allowedHosts=$allowedHosts",
            )
        }

        return parsed
    }
}
