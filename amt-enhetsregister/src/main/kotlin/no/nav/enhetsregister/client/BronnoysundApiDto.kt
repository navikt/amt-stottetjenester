package no.nav.enhetsregister.client

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.enhetsregister.domain.EnhetOppdatering
import no.nav.enhetsregister.domain.EnhetOppdateringType
import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import java.time.ZonedDateTime

data class ModerenhetDto(
    val organisasjonsnummer: String,
    val navn: String,
    val slettedato: String?,
) {
    fun toDomain() = Moderenhet(
        organisasjonsnummer = organisasjonsnummer,
        navn = navn,
        slettedato = slettedato,
    )
}

data class UnderenhetDto(
    val organisasjonsnummer: String,
    val navn: String,
    val slettedato: String?,
    val overordnetEnhet: String?,
) {
    fun toDomain() = Underenhet(
        organisasjonsnummer = organisasjonsnummer,
        navn = navn,
        slettedato = slettedato,
        overordnetEnhet = overordnetEnhet,
    )
}

data class EnhetOppdateringDto(
    val oppdateringsid: Int,
    val dato: String,
    val organisasjonsnummer: String,
    val endringstype: String,
) {
    fun toDomain() = EnhetOppdatering(
        oppdateringId = oppdateringsid,
        organisasjonsnummer = organisasjonsnummer,
        endringstype =
            when (endringstype) {
                "Ny" -> EnhetOppdateringType.NY
                "Ukjent" -> EnhetOppdateringType.UKJENT
                "Fjernet" -> EnhetOppdateringType.FJERNET
                "Sletting" -> EnhetOppdateringType.SLETTING
                "Endring" -> EnhetOppdateringType.ENDRING
                else -> throw IllegalArgumentException("Ukjent EnhetOppdateringType: $endringstype")
            },
        dato = ZonedDateTime.parse(dato),
    )
}

data class HentModerenhetOppdateringerResponse(
    @field:JsonProperty("_embedded") val embedded: Embedded?,
) {
    data class Embedded(
        val oppdaterteEnheter: List<EnhetOppdateringDto>,
    )
}

data class HentUnderenhetOppdateringerResponse(
    @field:JsonProperty("_embedded") val embedded: Embedded?,
) {
    data class Embedded(
        val oppdaterteUnderenheter: List<EnhetOppdateringDto>,
    )
}
