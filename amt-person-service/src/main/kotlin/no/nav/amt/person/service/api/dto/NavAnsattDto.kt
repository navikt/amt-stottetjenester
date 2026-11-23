package no.nav.amt.person.service.api.dto

import no.nav.amt.person.service.navansatt.NavAnsattDbo
import java.util.UUID

/**
 * Denne klassen bør kunne fjernes som duplikat.
 *
 * Per 2026-11-13 har [no.nav.amt.lib.models.person.NavAnsatt]
 * identisk definisjon. Men å bytte ut med modell-klassen fører til uheldig sammenblanding av modell- og request-laget.
 *
 * TODO: erstatt med [no.nav.amt.lib.models.person.dto.NavAnsattDto] - men denne har ulik casing av navident-feltet,
 *   så det krever potensielt endringer i person-servicens klienter.
 */
data class NavAnsattDto(
    val id: UUID,
    val navIdent: String,
    val navn: String,
    val epost: String?,
    val telefon: String?,
    val navEnhetId: UUID?,
) {
    companion object {
        fun fromDbo(source: NavAnsattDbo): NavAnsattDto = NavAnsattDto(
            id = source.id,
            navIdent = source.navIdent,
            navn = source.navn,
            epost = source.epost,
            telefon = source.telefon,
            navEnhetId = source.navEnhetId,
        )
    }
}
