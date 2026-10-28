package no.nav.amt.person.service.api.dto

import no.nav.amt.person.service.person.dbo.PersonDbo
import java.util.UUID

data class ArrangorAnsattDto(
    val id: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
) {
    companion object {
        fun fromDbo(source: PersonDbo) = ArrangorAnsattDto(
            id = source.id,
            personident = source.personident,
            fornavn = source.fornavn,
            mellomnavn = source.mellomnavn,
            etternavn = source.etternavn,
        )
    }
}
