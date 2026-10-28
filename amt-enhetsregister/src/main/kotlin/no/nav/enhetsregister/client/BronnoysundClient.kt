package no.nav.enhetsregister.client

import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.enhetsregister.domain.EnhetOppdatering
import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class BronnoysundClient(
    private val bronnoysundApi: BronnoysundApi,
    private val bulkDownloader: BronnoysundBulkDownloader,
) {
    fun hentModerenhetOppdateringer(
        fraOppdateringId: Int,
        size: Int,
    ): List<EnhetOppdatering> {
        val oppdateringer = executeUpstreamCallWithRequiredBody(
            serviceName = "BRREG",
            operation = "hent moderenhet oppdateringer",
        ) { bronnoysundApi.hentModerenhetOppdateringer(fraOppdateringId, size) }

        return oppdateringer.embedded
            ?.oppdaterteEnheter
            ?.map { it.toDomain() }
            .orEmpty()
    }

    fun hentUnderenhetOppdateringer(
        fraOppdateringId: Int,
        size: Int,
    ): List<EnhetOppdatering> {
        val oppdateringer = executeUpstreamCallWithRequiredBody(
            serviceName = "BRREG",
            operation = "hent underenhet oppdateringer",
        ) { bronnoysundApi.hentUnderenhetOppdateringer(fraOppdateringId, size) }

        return oppdateringer.embedded
            ?.oppdaterteUnderenheter
            ?.map { it.toDomain() }
            .orEmpty()
    }

    fun hentModerenhet(organisasjonsnummer: String): Moderenhet? = hentHvisFinnes(
        operation = "hent moderenhet",
        kall = { bronnoysundApi.hentModerenhet(organisasjonsnummer) },
    )?.toDomain()

    fun hentUnderenhet(organisasjonsnummer: String): Underenhet? = hentHvisFinnes(
        operation = "hent underenhet",
        kall = { bronnoysundApi.hentUnderenhet(organisasjonsnummer) },
    )?.toDomain()

    fun hentAlleModerenheter(): List<Moderenhet> = bulkDownloader.hentAlleModerenheter()

    fun hentAlleUnderenheter(): List<Underenhet> = bulkDownloader.hentAlleUnderenheter()

    private fun <T : Any> hentHvisFinnes(
        operation: String,
        kall: () -> ResponseEntity<T>,
    ): T? = try {
        executeUpstreamCallWithRequiredBody(
            serviceName = "BRREG",
            operation = operation,
        ) { kall() }
    } catch (e: UpstreamServiceException) {
        if (e.statusCode in MISSING_ENTITY_STATUSES) {
            null
        } else {
            throw e
        }
    }

    companion object {
        private val MISSING_ENTITY_STATUSES = setOf(HttpStatus.GONE.value(), HttpStatus.NOT_FOUND.value())
    }
}
