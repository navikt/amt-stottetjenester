package no.nav.amt.person.service.clients.veilarboppfolging

import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.utils.toSystemZoneLocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class VeilarboppfolgingClient(
    private val api: VeilarboppfolgingApi,
) {
    fun hentVeilederIdent(fnr: String): String? {
        val response = executeUpstreamCall(
            serviceName = "veilarboppfolging",
            operation = "hent veileder",
        ) { api.hentVeileder(VeilarboppfolgingApi.PersonRequest(fnr)) }

        if (response.statusCode == HttpStatus.NO_CONTENT) return null
        return response.body?.veilederIdent
    }

    fun hentOppfolgingperioder(fnr: String): List<Oppfolgingsperiode> = executeUpstreamCallWithRequiredBody(
        serviceName = "veilarboppfolging",
        operation = "hent oppfølgingsperioder",
    ) {
        api.hentOppfolgingsperioder(VeilarboppfolgingApi.PersonRequest(fnr))
    }.map {
        Oppfolgingsperiode(
            id = it.uuid,
            startdato = it.startDato.toSystemZoneLocalDateTime(),
            sluttdato = it.sluttDato?.toSystemZoneLocalDateTime(),
        )
    }
}
