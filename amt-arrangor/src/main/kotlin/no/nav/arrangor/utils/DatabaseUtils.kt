package no.nav.arrangor.utils

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

fun <V> sqlParameters(vararg pairs: Pair<String, V>): MapSqlParameterSource = MapSqlParameterSource().addValues(pairs.toMap())

fun ResultSet.getNullableUUID(columnLabel: String): UUID? = this.getString(columnLabel)?.let { UUID.fromString(it) }

fun Timestamp.toSystemZoneLocalDateTime(): LocalDateTime = this
    .toInstant()
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()

fun Timestamp.toSystemZonedDateTime(): ZonedDateTime = this
    .toInstant()
    .atZone(ZoneId.systemDefault())

fun ResultSet.getNullableZonedDateTime(columnLabel: String): ZonedDateTime? = this.getTimestamp(columnLabel)?.toSystemZonedDateTime()

/** Binder tidspunktet som UTC, uavhengig av JVM-ens og databasens tidssoner. */
fun ZonedDateTime.toSqlOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
