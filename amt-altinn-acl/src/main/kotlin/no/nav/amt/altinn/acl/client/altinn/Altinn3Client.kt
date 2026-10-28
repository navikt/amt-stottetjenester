package no.nav.amt.altinn.acl.client.altinn

import no.nav.amt.altinn.acl.client.exception.AltinnClientException
import no.nav.amt.altinn.acl.client.exception.MaskinportenTokenException
import no.nav.amt.altinn.acl.config.ALTINN3_CLIENT_ID
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Service
class Altinn3Client(
    private val altinnApi: AltinnApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentRoller(
        norskIdent: String,
        roller: List<RolleType>,
    ): Map<RolleType, List<String>> {
        val parties = hentAuthorizedParties(norskIdent)
        val resourceIds = roller.map { it.resourceId }.toSet()

        return roller
            .associateWith { rolle ->
                parties
                    .flatMap { it.finnTilganger(resourceIds) }
                    .filter { it.rolle == rolle }
                    .map { it.organisasjonsnummer }
            }.also {
                log.info("Hentet ${it.values.sumOf { strings -> strings.size }} $roller tilganger fra Altinn 3")
            }
    }

    private fun hentAuthorizedParties(norskIdent: String): List<AuthorizedParty> = try {
        executeUpstreamCallWithRequiredBody(
            ALTINN3_CLIENT_ID,
            "hentAuthorizedParties",
        ) {
            altinnApi.hentAuthorizedParties(AuthorizedPartiesRequest(norskIdent))
        }
    } catch (e: MaskinportenTokenException) {
        throw AltinnClientException(
            statusCode = e.statusCode,
            errorCode = e.errorCode,
        )
    } catch (e: UpstreamServiceException) {
        when (val cause = e.cause) {
            is RestClientResponseException -> throw AltinnClientException(
                statusCode = cause.statusCode.value(),
            )

            else -> throw e
        }
    }
}
