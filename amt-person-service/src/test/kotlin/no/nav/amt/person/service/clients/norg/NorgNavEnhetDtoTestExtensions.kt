package no.nav.amt.person.service.clients.norg

import no.nav.amt.person.service.navenhet.NavEnhetDbo

fun NavEnhetDbo.toNorgNavEnhetDto() = NorgNavEnhetDto(
    enhetNr = enhetId,
    navn = navn,
)
