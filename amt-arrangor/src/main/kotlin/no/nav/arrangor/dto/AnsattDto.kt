package no.nav.arrangor.dto

import no.nav.arrangor.domain.Personalia
import no.nav.arrangor.domain.TilknyttetArrangor
import java.util.UUID

data class AnsattDto(
    val id: UUID,
    val source: String?,
    val personalia: Personalia,
    val arrangorer: List<TilknyttetArrangor>,
)
