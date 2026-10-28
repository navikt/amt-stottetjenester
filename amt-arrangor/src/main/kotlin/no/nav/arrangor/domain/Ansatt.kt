package no.nav.arrangor.domain

import java.util.UUID

data class Ansatt(
    val id: UUID,
    val personalia: Personalia,
    val arrangorer: List<TilknyttetArrangor>,
)

data class Personalia(
    val personident: String,
    val personId: UUID?,
    val navn: Navn,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class TilknyttetArrangor(
    val arrangorId: UUID,
    val arrangor: Arrangor?,
    val overordnetArrangor: Arrangor?,
    val roller: List<AnsattRolle>,
    val veileder: List<Veileder>,
    val koordinator: List<UUID>,
)

data class Veileder(
    val deltakerId: UUID,
    val type: VeilederType,
)

enum class AnsattRolle {
    KOORDINATOR,
    VEILEDER,
}

enum class VeilederType {
    VEILEDER,
    MEDVEILEDER,
}
