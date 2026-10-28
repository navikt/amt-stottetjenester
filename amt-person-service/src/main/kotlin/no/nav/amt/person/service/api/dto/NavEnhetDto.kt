package no.nav.amt.person.service.api.dto

import no.nav.amt.person.service.navenhet.NavEnhetDbo
import java.util.UUID

data class NavEnhetDto(
    val id: UUID,
    val enhetId: String,
    val navn: String,
) {
    companion object {
        fun fromDbo(source: NavEnhetDbo) = NavEnhetDto(
            id = source.id,
            enhetId = source.enhetId,
            navn = source.navn,
        )
    }
}
