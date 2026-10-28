package no.nav.arrangor.kafka.model

import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val status: DeltakerStatus,
)

data class DeltakerStatus(
    val type: DeltakerStatusType,
    val gyldigFra: LocalDateTime,
    val opprettetDato: LocalDateTime,
)

enum class DeltakerStatusType {
    VENTER_PA_OPPSTART,
    DELTAR,
    HAR_SLUTTET,
    IKKE_AKTUELL,
    FEILREGISTRERT,
    SOKT_INN,
    VURDERES,
    VENTELISTE,
    AVBRUTT,
    FULLFORT,
    PABEGYNT_REGISTRERING,
    UTKAST_TIL_PAMELDING,
    AVBRUTT_UTKAST,
}

val AVSLUTTENDE_STATUSER =
    listOf(
        DeltakerStatusType.HAR_SLUTTET,
        DeltakerStatusType.FULLFORT,
        DeltakerStatusType.IKKE_AKTUELL,
        DeltakerStatusType.AVBRUTT,
    )

val SKJULES_ALLTID_STATUSER =
    listOf(
        DeltakerStatusType.SOKT_INN,
        DeltakerStatusType.VENTELISTE,
        DeltakerStatusType.PABEGYNT_REGISTRERING,
        DeltakerStatusType.FEILREGISTRERT,
        DeltakerStatusType.UTKAST_TIL_PAMELDING,
        DeltakerStatusType.AVBRUTT_UTKAST,
    )
