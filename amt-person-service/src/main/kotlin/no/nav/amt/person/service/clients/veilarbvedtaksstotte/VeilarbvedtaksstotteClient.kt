package no.nav.amt.person.service.clients.veilarbvedtaksstotte

import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import org.springframework.stereotype.Service

@Service
class VeilarbvedtaksstotteClient(
    private val api: VeilarbvedtaksstotteApi,
) {
    fun hentInnsatsgruppe(fnr: String): InnsatsgruppeV1? = executeUpstreamCall(
        serviceName = "veilarbvedtaksstotte",
        operation = "hent innsatsgruppe",
    ) {
        api
            .hentGjeldende14aVedtak(VeilarbvedtaksstotteApi.PersonRequest(fnr))
            ?.innsatsgruppe
            ?.toV1()
    }
}
