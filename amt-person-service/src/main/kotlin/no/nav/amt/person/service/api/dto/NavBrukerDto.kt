package no.nav.amt.person.service.api.dto

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navenhet.toDto
import java.util.UUID

data class NavBrukerDto(
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val harFalskIdentitet: Boolean,
    val navVeilederId: UUID?,
    val navEnhet: NavEnhetDto?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: Innsatsgruppe?,
) {
    companion object {
        fun fromDbo(source: NavBrukerDbo) = NavBrukerDto(
            personId = source.person.id,
            personident = source.person.personident,
            fornavn = source.person.fornavn,
            mellomnavn = source.person.mellomnavn,
            etternavn = source.person.etternavn,
            harFalskIdentitet = source.person.erFalskIdentitet,
            navVeilederId = source.navVeileder?.id,
            navEnhet = source.navEnhet?.toDto(),
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
