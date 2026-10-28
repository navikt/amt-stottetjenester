package no.nav.amt.person.service.navbruker

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Oppfolgingsperiode(
    val id: UUID,
    val startdato: LocalDateTime,
    val sluttdato: LocalDateTime?,
) {
    fun erAktiv(): Boolean {
        val now = LocalDate.now()
        return !(now.isBefore(startdato.toLocalDate()) || (sluttdato != null && !now.isBefore(sluttdato.toLocalDate())))
    }
}

fun harAktivOppfolgingsperiode(oppfolgingsperioder: List<Oppfolgingsperiode>) = oppfolgingsperioder.any { it.erAktiv() }
