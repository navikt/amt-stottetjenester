package no.nav.amt.person.service.clients.nom

import java.time.LocalDate

object NomQueries {
    enum class ResultCode {
        OK,
        NOT_FOUND,
        ERROR,
    }

    data class RessursResult(
        val code: ResultCode,
        val ressurs: Ressurs?,
    )

    data class Ressurs(
        val navident: String,
        val visningsnavn: String?,
        val fornavn: String?,
        val etternavn: String?,
        val epost: String?,
        val telefon: List<Telefon>,
        // Udokumentert verdi: Det eneste Nom er master for av telefoner er det ansatte selv velger å legge inn på primaryTelefon feltet,
        // men det er ikke obligatorisk og ikke primærnummer, med mindre de er eksterne
        val primaryTelefon: String?,
        val orgTilknytning: List<OrgTilknytning>,
    )

    data class OrgTilknytning(
        val gyldigFom: LocalDate,
        val gyldigTom: LocalDate?,
        val orgEnhet: OrgEnhet,
        val erDagligOppfolging: Boolean,
    ) {
        data class OrgEnhet(
            val remedyEnhetId: String?,
        )
    }

    data class Telefon(
        val nummer: String,
        val type: String, // Enten NAV_TJENESTE_TELEFON eller PRIVAT_TELEFON
    )
}
