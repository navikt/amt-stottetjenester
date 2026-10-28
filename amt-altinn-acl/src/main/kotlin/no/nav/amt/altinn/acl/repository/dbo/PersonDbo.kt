package no.nav.amt.altinn.acl.repository.dbo

import java.time.ZonedDateTime

data class PersonDbo(
    val id: Long,
    val norskIdent: String,
    val created: ZonedDateTime,
    val lastSynchronized: ZonedDateTime,
    val synchronizationAttempt: Long,
    val appliedSynchronizationAttempt: Long,
)
