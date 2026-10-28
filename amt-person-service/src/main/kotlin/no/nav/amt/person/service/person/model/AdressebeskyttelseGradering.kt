package no.nav.amt.person.service.person.model

enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG,
    FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
    UGRADERT,
    ;

    fun erBeskyttet(): Boolean = this != UGRADERT
}
