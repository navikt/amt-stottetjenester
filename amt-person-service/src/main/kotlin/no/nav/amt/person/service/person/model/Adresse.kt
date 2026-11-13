package no.nav.amt.person.service.person.model

import no.nav.amt.lib.models.person.address.Bostedsadresse
import no.nav.amt.lib.models.person.address.Oppholdsadresse
import no.nav.amt.lib.models.person.address.Postboksadresse
import no.nav.amt.lib.models.person.address.Vegadresse

data class Adresse(
    val bostedsadresse: Bostedsadresse?,
    val oppholdsadresse: Oppholdsadresse?,
    val kontaktadresse: Kontaktadresse?,
)

data class Kontaktadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val postboksadresse: Postboksadresse?,
)
