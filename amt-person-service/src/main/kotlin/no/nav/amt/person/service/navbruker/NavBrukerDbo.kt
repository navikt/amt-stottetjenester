package no.nav.amt.person.service.navbruker

import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Adresse
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
    val innsatsgruppe: InnsatsgruppeV1?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
)
