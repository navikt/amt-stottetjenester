package no.nav.amt.person.service.person

import no.nav.amt.person.service.person.dbo.PersonDbo

data class PersonUpdateEvent(
    val person: PersonDbo,
)
