package no.nav.amt.person.service.navbruker

import no.nav.amt.lib.models.person.Oppfolgingsperiode

fun harAktivOppfolgingsperiode(oppfolgingsperioder: List<Oppfolgingsperiode>) = oppfolgingsperioder.any { it.erAktiv() }
