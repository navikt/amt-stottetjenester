package no.nav.amt.altinn.acl.domain

import java.time.ZonedDateTime

data class Rolle(
    val id: Long,
    val rolleType: RolleType,
    val validFrom: ZonedDateTime,
    val validTo: ZonedDateTime?,
)
