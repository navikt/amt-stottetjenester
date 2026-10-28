package no.nav.amt.person.service.utils

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun ZonedDateTime.toSystemZoneLocalDateTime(): LocalDateTime = this
    .withZoneSameInstant(ZoneId.systemDefault())
    .toLocalDateTime()
