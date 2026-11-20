package no.nav.amt.person.service.kafka.consumer.dto

import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.utils.toSystemZoneLocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

// https://github.com/navikt/veilarboppfolging/blob/2158ce53fcf1d7efae8df12f7506061a2b52fa71/src/main/kotlin/no/nav/veilarboppfolging/service/ArbeidsoppfolgingsKontorEndretService.kt#L121
data class SisteOppfolgingsperiodePayload(
    val oppfolgingsperiodeUuid: UUID,
    val ident: String,
    val kontor: KontorPayload?,
    val sisteEndringsType: String,
    val startTidspunkt: ZonedDateTime,
    val sluttTidspunkt: ZonedDateTime?,
) {
    fun toOppfolgingsperiode() = Oppfolgingsperiode(
        id = oppfolgingsperiodeUuid,
        startdato = startTidspunkt.toSystemZoneLocalDateTime(),
        sluttdato = sluttTidspunkt?.toSystemZoneLocalDateTime(),
    )
}

data class KontorPayload(
    val kontorId: String,
    val kontorNavn: String,
)
