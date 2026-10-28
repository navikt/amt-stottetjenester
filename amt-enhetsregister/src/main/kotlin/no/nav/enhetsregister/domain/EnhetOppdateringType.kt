package no.nav.enhetsregister.domain

enum class EnhetOppdateringType {
    UKJENT, // Ukjent type endring. Ofte fordi endringen har skjedd før endringstype ble innført.
    NY, // Enheten har blitt lagt til i Enhetsregisteret
    ENDRING, // Enheten har blitt endret i Enhetsregisteret
    SLETTING, // Enheten har blitt slettet fra Enhetsregisteret
    FJERNET, // Enheten har blitt fjernet fra Åpne Data. Eventuelle kopier skal også fjerne enheten.
}
