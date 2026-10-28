package no.nav.amt.person.service.kafka.producer.dto

import no.nav.amt.person.service.navansatt.NavAnsattDbo
import java.util.UUID

data class NavAnsattDtoV1(
    val id: UUID,
    val navident: String,
    val navn: String,
    val telefon: String?,
    val epost: String?,
    val navEnhetId: UUID?,
) {
    companion object {
        fun fromDbo(source: NavAnsattDbo) = NavAnsattDtoV1(
            id = source.id,
            navident = source.navIdent,
            navn = source.navn,
            telefon = source.telefon,
            epost = source.epost,
            navEnhetId = source.navEnhetId,
        )
    }
}
