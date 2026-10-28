package no.nav.arrangor.client.altinn

import no.nav.arrangor.domain.AnsattRolle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AltinnAclClient(
    private val altinnAclApi: AltinnAclApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentRoller(personident: String): Result<List<AltinnRolle>> = runCatching {
        altinnAclApi
            .hentRoller(AltinnAclApi.HentRollerRequest(personident))
            .roller
            .map { roller ->
                AltinnRolle(
                    organisasjonsnummer = roller.organisasjonsnummer,
                    roller = roller.roller.map(::mapTiltaksarrangorRolle),
                )
            }.also { log.debug("Hentet roller for person") }
    }

    private fun mapTiltaksarrangorRolle(rolle: String): AnsattRolle = when (rolle) {
        "KOORDINATOR" -> AnsattRolle.KOORDINATOR
        "VEILEDER" -> AnsattRolle.VEILEDER
        else -> throw UkjentAltinnRolleException(rolle)
    }
}
