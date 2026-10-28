package no.nav.amt.person.service.clients.kodeverk

data class GetKodeverkKoderBetydningerResponse(
    val betydninger: Map<String, List<Betydning>>,
) {
    data class Betydning(
        val beskrivelser: Map<String, Beskrivelse>,
    ) {
        data class Beskrivelse(
            val term: String,
        )
    }
}
