package no.nav.amt.person.service.navansatt

import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.dto.NavAnsattDto
import java.time.LocalDateTime
import java.util.UUID

data class NavAnsattDbo(
    val id: UUID,
    val navIdent: String,
    val navn: String,
    val telefon: String?,
    val epost: String?,
    val navEnhetId: UUID?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
)

fun NavAnsattDbo.toModel() = NavAnsatt(
    id = id,
    navIdent = navIdent,
    navn = navn,
    epost = epost,
    telefon = telefon,
    navEnhetId = navEnhetId,
)

fun NavAnsattDbo.toDto() = NavAnsattDto(
    id = id,
    navident = navIdent,
    navn = navn,
    epost = epost,
    telefon = telefon,
    navEnhetId = navEnhetId,
)
