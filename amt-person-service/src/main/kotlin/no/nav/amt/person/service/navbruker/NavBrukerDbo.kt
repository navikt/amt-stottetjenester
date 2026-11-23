package no.nav.amt.person.service.navbruker

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.navenhet.toDto
import no.nav.amt.person.service.person.dbo.PersonDbo
import java.time.LocalDateTime
import java.util.UUID

data class NavBrukerDbo(
    val id: UUID,
    val person: PersonDbo,
    val navVeileder: NavAnsattDbo?,
    val navEnhet: NavEnhetDbo?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val sisteKrrSync: LocalDateTime?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val innsatsgruppe: Innsatsgruppe?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
)

fun NavBrukerDbo.toDto(): NavBrukerDto = NavBrukerDto(
    personId = person.id,
    personident = person.personident,
    fornavn = person.fornavn,
    mellomnavn = person.mellomnavn,
    etternavn = person.etternavn,
    navVeilederId = navVeileder?.id,
    navEnhet = navEnhet?.toDto(),
    telefon = telefon,
    epost = epost,
    erSkjermet = erSkjermet,
    adresse = adresse,
    adressebeskyttelse = adressebeskyttelse,
    oppfolgingsperioder = oppfolgingsperioder,
    innsatsgruppe = innsatsgruppe,
)
