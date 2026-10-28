package no.nav.amt.person.service.kafka.producer.dto

import no.nav.amt.person.service.navenhet.NavEnhetDbo
import java.util.UUID

data class NavEnhetDtoV1(
    val id: UUID,
    val enhetId: String,
    val navn: String,
) {
    companion object {
        fun fromDbo(source: NavEnhetDbo) = NavEnhetDtoV1(
            id = source.id,
            enhetId = source.enhetId,
            navn = source.navn,
        )
    }
}
