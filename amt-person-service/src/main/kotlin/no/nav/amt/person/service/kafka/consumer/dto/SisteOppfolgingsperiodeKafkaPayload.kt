package no.nav.amt.person.service.kafka.consumer.dto

import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.utils.toSystemZoneLocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

data class SisteOppfolgingsperiodeKafkaPayload(
    val uuid: UUID,
    val aktorId: String,
    val startDato: ZonedDateTime,
    val sluttDato: ZonedDateTime?,
) {
    fun toOppfolgingsperiode() = Oppfolgingsperiode(
        id = uuid,
        startdato = startDato.toSystemZoneLocalDateTime(),
        sluttdato = sluttDato?.toSystemZoneLocalDateTime(),
    )
}
