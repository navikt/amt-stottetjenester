package no.nav.amt.person.service.kafka.producer.dto

import no.nav.amt.person.service.navbruker.Adressebeskyttelse
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.person.model.Adresse
import java.util.UUID

data class NavBrukerDtoV1(
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navVeilederId: UUID?,
    val navEnhet: NavEnhetDtoV1?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: InnsatsgruppeV1?,
) {
    companion object {
        fun fromDbo(source: NavBrukerDbo) = NavBrukerDtoV1(
            personId = source.person.id,
            personident = source.person.personident,
            fornavn = source.person.fornavn,
            mellomnavn = source.person.mellomnavn,
            etternavn = source.person.etternavn,
            navVeilederId = source.navVeileder?.id,
            navEnhet = source.navEnhet?.let { NavEnhetDtoV1.fromDbo(it) },
            telefon = source.telefon,
            epost = source.epost,
            erSkjermet = source.erSkjermet,
            adresse = source.adresse,
            adressebeskyttelse = source.adressebeskyttelse,
            oppfolgingsperioder = source.oppfolgingsperioder,
            innsatsgruppe = source.innsatsgruppe,
        )
    }
}
