package no.nav.amt.person.service.api.dto

import no.nav.amt.person.service.navansatt.NavAnsattDbo
import java.util.UUID

data class NavAnsattDto(
    val id: UUID,
    val navIdent: String,
    val navn: String,
    val epost: String?,
    val telefon: String?,
    val navEnhetId: UUID?,
) {
    companion object {
        fun fromDbo(source: NavAnsattDbo) = NavAnsattDto(
            id = source.id,
            navIdent = source.navIdent,
            navn = source.navn,
            epost = source.epost,
            telefon = source.telefon,
            navEnhetId = source.navEnhetId,
        )
    }
}
