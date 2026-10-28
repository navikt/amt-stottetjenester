package no.nav.amt.altinn.acl.utils

import java.sql.ResultSet
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun ResultSet.getNullableZonedDateTime(columnLabel: String): ZonedDateTime? {
    val timestamp = this.getTimestamp(columnLabel) ?: return null
    return timestamp.toInstant().atZone(ZoneOffset.systemDefault())
}

fun ResultSet.getZonedDateTime(columnLabel: String): ZonedDateTime = getNullableZonedDateTime(columnLabel)
    ?: throw IllegalStateException("Expected $columnLabel not to be null")
