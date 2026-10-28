package no.nav.amt.person.service.clients.pdl

import no.nav.amt.person.service.person.dbo.PersonDbo.Companion.UNKNOWN_NAME
import no.nav.amt.person.service.person.model.Adresse
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.Bostedsadresse
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Kontaktadresse
import no.nav.amt.person.service.person.model.Matrikkeladresse
import no.nav.amt.person.service.person.model.Oppholdsadresse
import no.nav.amt.person.service.person.model.Personident
import no.nav.amt.person.service.person.model.Postboksadresse
import no.nav.amt.person.service.person.model.Vegadresse
import no.nav.amt.person.service.poststed.Postnummer

fun toPdlBruker(
    hentPerson: PdlQueries.HentPersonResult,
    hentIdenter: PdlQueries.HentIdenterResult,
    postnummerTilPoststedFunc: (Set<String>) -> List<Postnummer>,
): PdlPerson {
    val navn = hentPerson.navn.toNavnMedFallback()

    return PdlPerson(
        erFalskIdentitet = hentPerson.falskIdentitet?.erFalsk ?: false,
        fornavn = navn.fornavn,
        mellomnavn = navn.mellomnavn,
        etternavn = navn.etternavn,
        telefonnummer = hentPerson.telefonnummer.toTelefonnummer(),
        adressebeskyttelseGradering = hentPerson.adressebeskyttelse.toDiskresjonskode(),
        identer = hentIdenter.identer.map {
            Personident(
                it.ident,
                it.historisk,
                IdentType.valueOf(it.gruppe),
            )
        },
        adresse = hentPerson.toAdresse(postnummerTilPoststedFunc),
    )
}

fun List<PdlQueries.Attribute.Adressebeskyttelse>.toDiskresjonskode(): AdressebeskyttelseGradering? = when (this.firstOrNull()?.gradering) {
    "STRENGT_FORTROLIG_UTLAND" -> AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
    "STRENGT_FORTROLIG" -> AdressebeskyttelseGradering.STRENGT_FORTROLIG
    "FORTROLIG" -> AdressebeskyttelseGradering.FORTROLIG
    "UGRADERT" -> AdressebeskyttelseGradering.UGRADERT
    else -> null
}

fun List<PdlQueries.Attribute.Telefonnummer>.toTelefonnummer(): String? {
    val prioritertNummer = this.minByOrNull { it.prioritet } ?: return null
    return "${prioritertNummer.landskode}${prioritertNummer.nummer}"
}

private fun PdlQueries.HentPersonResult.toAdresse(postnummerTilPoststedFunc: (Set<String>) -> List<Postnummer>): Adresse? {
    val kontaktadresseFraPdl = kontaktadresse.firstOrNull()
    val bostedsadresseFraPdl = bostedsadresse.firstOrNull()
    val oppholdsadresseFraPdl = oppholdsadresse.firstOrNull()

    val unikePostnummer =
        setOfNotNull(
            kontaktadresseFraPdl?.vegadresse?.postnummer,
            kontaktadresseFraPdl?.postboksadresse?.postnummer,
            bostedsadresseFraPdl?.vegadresse?.postnummer,
            bostedsadresseFraPdl?.matrikkeladresse?.postnummer,
            oppholdsadresseFraPdl?.vegadresse?.postnummer,
            oppholdsadresseFraPdl?.matrikkeladresse?.postnummer,
        )

    val poststeder = postnummerTilPoststedFunc(unikePostnummer)

    if (poststeder.isEmpty()) return null

    val adresse = Adresse(
        bostedsadresse = bostedsadresseFraPdl?.toBostedsadresse(poststeder),
        oppholdsadresse = oppholdsadresseFraPdl?.toOppholdsadresse(poststeder),
        kontaktadresse = kontaktadresseFraPdl?.toKontaktadresse(poststeder),
    )

    if (adresse.bostedsadresse == null && adresse.oppholdsadresse == null && adresse.kontaktadresse == null) {
        return null
    }
    return adresse
}

internal fun List<PdlQueries.Attribute.Navn>.toNavnMedFallback(): PdlQueries.Attribute.Navn =
    this.firstOrNull() ?: PdlQueries.Attribute.Navn(
        fornavn = UNKNOWN_NAME,
        mellomnavn = null,
        etternavn = UNKNOWN_NAME,
    )

private fun PdlQueries.Attribute.Bostedsadresse.toBostedsadresse(poststeder: List<Postnummer>): Bostedsadresse? {
    if (vegadresse == null && matrikkeladresse == null) {
        return null
    }
    val bostedsadresse =
        Bostedsadresse(
            coAdressenavn = coAdressenavn,
            vegadresse = vegadresse?.toVegadresse(poststeder),
            matrikkeladresse = matrikkeladresse?.toMatrikkeladresse(poststeder),
        )
    if (bostedsadresse.vegadresse == null && bostedsadresse.matrikkeladresse == null) {
        return null
    }
    return bostedsadresse
}

private fun PdlQueries.Attribute.Oppholdsadresse.toOppholdsadresse(poststeder: List<Postnummer>): Oppholdsadresse? {
    if (vegadresse == null && matrikkeladresse == null) {
        return null
    }
    val oppholdsadresse =
        Oppholdsadresse(
            coAdressenavn = coAdressenavn,
            vegadresse = vegadresse?.toVegadresse(poststeder),
            matrikkeladresse = matrikkeladresse?.toMatrikkeladresse(poststeder),
        )
    if (oppholdsadresse.vegadresse == null && oppholdsadresse.matrikkeladresse == null) {
        return null
    }
    return oppholdsadresse
}

private fun PdlQueries.Attribute.Kontaktadresse.toKontaktadresse(poststeder: List<Postnummer>): Kontaktadresse? {
    if (vegadresse == null && postboksadresse == null) {
        return null
    }
    val kontaktadresse = Kontaktadresse(
        coAdressenavn = coAdressenavn,
        vegadresse = vegadresse?.toVegadresse(poststeder),
        postboksadresse = postboksadresse?.toPostboksadresse(poststeder),
    )
    if (kontaktadresse.vegadresse == null && kontaktadresse.postboksadresse == null) {
        return null
    }
    return kontaktadresse
}

private fun PdlQueries.Attribute.Vegadresse.toVegadresse(poststeder: List<Postnummer>): Vegadresse? {
    if (postnummer == null) {
        return null
    }
    val poststed = poststeder.find { it.postnummer == postnummer } ?: return null

    return Vegadresse(
        husnummer = husnummer,
        husbokstav = husbokstav,
        adressenavn = adressenavn,
        tilleggsnavn = tilleggsnavn,
        postnummer = postnummer,
        poststed = poststed.poststed,
    )
}

private fun PdlQueries.Attribute.Matrikkeladresse.toMatrikkeladresse(poststeder: List<Postnummer>): Matrikkeladresse? {
    if (postnummer == null) {
        return null
    }
    val poststed = poststeder.find { it.postnummer == postnummer } ?: return null

    return Matrikkeladresse(
        tilleggsnavn = tilleggsnavn,
        postnummer = postnummer,
        poststed = poststed.poststed,
    )
}

private fun PdlQueries.Attribute.Postboksadresse.toPostboksadresse(poststeder: List<Postnummer>): Postboksadresse? {
    if (postnummer == null) {
        return null
    }
    val poststed = poststeder.find { it.postnummer == postnummer } ?: return null

    return Postboksadresse(
        postboks = postboks,
        postnummer = postnummer,
        poststed = poststed.poststed,
    )
}
