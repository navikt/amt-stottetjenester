package no.nav.amt.altinn.acl.domain

enum class RolleType(
    val resourceId: String,
) {
    KOORDINATOR("nav_tiltaksarrangor_deltakeroversikt-koordinator"),
    VEILEDER("nav_tiltaksarrangor_deltakeroversikt-veileder"),
    ;

    companion object {
        fun fromResourceId(id: String) = when (id) {
            KOORDINATOR.resourceId -> KOORDINATOR
            VEILEDER.resourceId -> VEILEDER
            else -> throw IllegalArgumentException("Ukjent ressursid $id")
        }
    }
}
