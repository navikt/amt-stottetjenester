package no.nav.amt.person.service.person.model

import no.nav.amt.person.service.person.dbo.PersonidentDbo
import java.util.UUID

data class Personident(
    val ident: String,
    val historisk: Boolean,
    val type: IdentType,
) {
    fun toDbo(personId: UUID) = PersonidentDbo(
        ident = ident,
        personId = personId,
        historisk = historisk,
        type = type,
    )

    companion object {
        fun List<Personident>.finnGjeldendeIdent(): Result<Personident> {
            val gjeldendeIdent = this.firstOrNull {
                !it.historisk && it.type == IdentType.FOLKEREGISTERIDENT
            } ?: this.firstOrNull {
                !it.historisk && it.type == IdentType.NPID
            }

            return gjeldendeIdent?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException("Ingen gjeldende personident finnes"))
        }
    }
}
