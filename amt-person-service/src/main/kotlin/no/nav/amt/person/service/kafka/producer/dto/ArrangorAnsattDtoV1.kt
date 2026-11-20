package no.nav.amt.person.service.kafka.producer.dto

import no.nav.amt.person.service.person.dbo.PersonDbo
import java.util.UUID

data class ArrangorAnsattDtoV1(
    val id: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
) {
    companion object {
        fun fromDbo(source: PersonDbo) = ArrangorAnsattDtoV1(
            id = source.id,
            personident = source.personident,
            fornavn = source.fornavn,
            mellomnavn = source.mellomnavn,
            etternavn = source.etternavn,
        )
    }
}
