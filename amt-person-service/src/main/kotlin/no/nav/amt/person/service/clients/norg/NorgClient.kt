package no.nav.amt.person.service.clients.norg

import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.DefaultUriBuilderFactory

@Service
class NorgClient(
    @Value($$"${spring.http.serviceclient.norg-api.base-url}") url: String,
    private val norgApi: NorgApi,
) {
    private val enhetIdPattern = Regex("^\\d{4}$")

    init {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Ugyldig url-skjema for norg-klient" }
    }

    private val uriBuilderFactory = DefaultUriBuilderFactory(url).apply {
        encodingMode = DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT
    }

    fun hentNavEnhet(enhetId: String): NorgNavEnhetDto? {
        val validatedEnhetId = validateEnhetId(enhetId)
        return try {
            executeUpstreamCallWithRequiredBody(
                serviceName = "NORG",
                operation = "hent enhet",
            ) { norgApi.hentNavEnhet(validatedEnhetId) }
        } catch (e: UpstreamServiceException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    fun hentNavEnheter(enheter: List<String>): List<NorgNavEnhetDto> {
        val validatedEnheter = enheter.map { validateEnhetId(it) }

        return executeUpstreamCallWithRequiredBody(
            serviceName = "NORG",
            operation = "hent enheter",
        ) {
            norgApi.hentNavEnheter(
                enhetsnummerListe = validatedEnheter.joinToString(","),
                uriBuilderFactory = uriBuilderFactory,
            )
        }
    }

    private fun validateEnhetId(enhetId: String): String {
        require(enhetIdPattern.matches(enhetId)) { "Ugyldig enhetId-format" }
        return enhetId
    }
}
