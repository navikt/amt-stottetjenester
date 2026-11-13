package no.nav.amt.person.service.clients.veilarbvedtaksstotte

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.toV1
import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import org.springframework.stereotype.Service

@Service
class VeilarbvedtaksstotteClient(
    private val api: VeilarbvedtaksstotteApi,
) {
    fun hentInnsatsgruppe(fnr: String): Innsatsgruppe? = executeUpstreamCall(
        serviceName = "veilarbvedtaksstotte",
        operation = "hent innsatsgruppe",
    ) {
        api
            .hentGjeldende14aVedtak(VeilarbvedtaksstotteApi.PersonRequest(fnr))
            ?.innsatsgruppe
            ?.toV1()
    }
}
