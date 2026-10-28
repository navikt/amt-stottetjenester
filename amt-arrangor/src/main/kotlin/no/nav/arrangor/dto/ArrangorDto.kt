package no.nav.arrangor.dto

import java.util.UUID

const val AMT_ARRANGOR_SOURCE = "amt-arrangor"

data class ArrangorDto(
    val id: UUID,
    val source: String?,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangorId: UUID?,
)
