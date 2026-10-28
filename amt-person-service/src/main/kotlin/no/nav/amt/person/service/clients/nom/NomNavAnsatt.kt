package no.nav.amt.person.service.clients.nom

import no.nav.amt.person.service.navansatt.NavAnsattDbo
import java.time.LocalDate
import java.util.UUID

data class NomNavAnsatt(
    val navIdent: String,
    val navn: String,
    val telefonnummer: String?,
    val epost: String?,
    private val orgTilknytning: List<NomQueries.OrgTilknytning>,
) {
    val navEnhetNummer: String?
        get() =
            orgTilknytning
                .filter { it.erDagligOppfolging && it.gyldigFom <= LocalDate.now() }
                .maxByOrNull { it.gyldigFom }
                ?.orgEnhet
                ?.remedyEnhetId

    fun toNavAnsatt(navEnhetId: UUID?) = NavAnsattDbo(
        id = UUID.randomUUID(),
        navIdent = navIdent,
        navn = navn,
        epost = epost,
        telefon = telefonnummer,
        navEnhetId = navEnhetId,
    )
}
