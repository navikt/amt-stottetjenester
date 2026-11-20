package no.nav.amt.kontrakter.person

import java.util.UUID

data class ArrangorAnsattDto(
    val id: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)
