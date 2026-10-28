package no.nav.amt.person.service.clients.kodeverk

import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.amt.person.service.poststed.Postnummer
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class KodeverkClient(
    private val api: KodeverkApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Retryable(includes = [RetryableUpstreamServiceException::class])
    fun hentKodeverk(): List<Postnummer> {
        val response = try {
            executeUpstreamCallWithRequiredBody(
                serviceName = "Kodeverk",
                operation = "hent postnummer",
            ) {
                api.hentPostnummerBetydninger(
                    ekskluderUgyldige = true,
                    oppslagsdato = LocalDate.now().toString(),
                    spraak = "nb",
                )
            }
        } catch (e: UpstreamServiceException) {
            log.error(
                "Noe gikk galt ved henting av postnummer fra Kodeverk (feiltype={}, status={})",
                e.javaClass.simpleName,
                e.statusCode,
            )
            throw e
        }

        return response.toPostnummerListe()
    }

    internal fun GetKodeverkKoderBetydningerResponse.toPostnummerListe(): List<Postnummer> = betydninger.map {
        Postnummer(
            postnummer = it.key,
            poststed = it.value
                .firstOrNull()
                ?.beskrivelser
                ?.get("nb")
                ?.term
                ?: throw RuntimeException("Kode ${it.key} mangler term"),
        )
    }
}
