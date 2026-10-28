package no.nav.amt.person.service.person.dbo

import no.nav.amt.person.service.person.model.IdentType
import java.time.LocalDateTime
import java.util.UUID

data class PersonidentDbo(
    val ident: String,
    val personId: UUID,
    val historisk: Boolean,
    val type: IdentType,
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
