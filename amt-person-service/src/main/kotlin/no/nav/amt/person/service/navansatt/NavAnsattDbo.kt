package no.nav.amt.person.service.navansatt

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
