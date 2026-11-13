package no.nav.amt.person.service.navenhet

import no.nav.amt.lib.models.person.dto.NavEnhetDto
import java.time.LocalDateTime
import java.util.UUID

data class NavEnhetDbo(
    val id: UUID,
    val enhetId: String,
    val navn: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
)

fun NavEnhetDbo.toDto() = NavEnhetDto(
    id = id,
    enhetId = enhetId,
    navn = navn,
)
