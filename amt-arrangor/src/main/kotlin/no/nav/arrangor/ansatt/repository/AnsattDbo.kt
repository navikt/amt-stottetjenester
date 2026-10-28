package no.nav.arrangor.ansatt.repository

import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Navn
import no.nav.arrangor.domain.Personalia
import no.nav.arrangor.domain.VeilederType
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

data class AnsattDbo(
    val id: UUID,
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    // Fylles av AnsattRepository fra normaliserte tabeller; lagres ikke i ansatt-tabellen.
    val arrangorer: List<ArrangorDbo>,
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val lastSynchronized: LocalDateTime = LocalDateTime.now(),
) {
    fun toPersonalia(): Personalia = Personalia(
        personident = personident,
        personId = personId,
        navn = Navn(
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
        ),
    )
}

data class ArrangorDbo(
    val arrangorId: UUID,
    val roller: List<RolleDbo>,
    val veileder: List<VeilederDeltakerDbo>,
    val koordinator: List<KoordinatorsDeltakerlisteDbo>,
)

data class RolleDbo(
    val rolle: AnsattRolle,
    val gyldigFra: ZonedDateTime = ZonedDateTime.now(),
    var gyldigTil: ZonedDateTime? = null,
) {
    fun erGyldig(): Boolean = gyldigTil?.isAfter(ZonedDateTime.now()) ?: true
}

data class KoordinatorsDeltakerlisteDbo(
    val deltakerlisteId: UUID,
    val gyldigFra: ZonedDateTime = ZonedDateTime.now(),
    var gyldigTil: ZonedDateTime? = null,
) {
    fun erGyldig(): Boolean = gyldigTil?.isAfter(ZonedDateTime.now()) ?: true
}

data class VeilederDeltakerDbo(
    val deltakerId: UUID,
    val veilederType: VeilederType,
    val gyldigFra: ZonedDateTime = ZonedDateTime.now(),
    var gyldigTil: ZonedDateTime? = null,
) {
    fun erGyldig(): Boolean = gyldigTil?.isAfter(ZonedDateTime.now()) ?: true
}
