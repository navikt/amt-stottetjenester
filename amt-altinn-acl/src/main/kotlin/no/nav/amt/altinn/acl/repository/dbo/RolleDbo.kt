package no.nav.amt.altinn.acl.repository.dbo

import no.nav.amt.altinn.acl.domain.RolleType
import java.time.ZonedDateTime

data class RolleDbo(
    val id: Long,
    val personId: Long,
    val organisasjonsnummer: String,
    val rolleType: RolleType,
    val validFrom: ZonedDateTime,
    val validTo: ZonedDateTime?,
)
