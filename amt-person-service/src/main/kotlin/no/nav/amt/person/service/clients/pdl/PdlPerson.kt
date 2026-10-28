package no.nav.amt.person.service.clients.pdl

import no.nav.amt.person.service.navbruker.Adressebeskyttelse
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Adresse
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.Personident
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import java.util.UUID

data class PdlPerson(
    val erFalskIdentitet: Boolean,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val telefonnummer: String?,
    val adressebeskyttelseGradering: AdressebeskyttelseGradering?,
    val identer: List<Personident>,
    val adresse: Adresse?,
) {
    fun getAdressebeskyttelse(): Adressebeskyttelse? = if (adressebeskyttelseGradering?.erBeskyttet() == true) {
        Adressebeskyttelse.valueOf(adressebeskyttelseGradering.name)
    } else {
        null
    }

    fun toPersonDbo(): PersonDbo {
        val gjeldendeIdent = identer.finnGjeldendeIdent().getOrThrow()

        return PersonDbo(
            id = UUID.randomUUID(),
            personident = gjeldendeIdent.ident,
            erFalskIdentitet = erFalskIdentitet,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
        )
    }
}
