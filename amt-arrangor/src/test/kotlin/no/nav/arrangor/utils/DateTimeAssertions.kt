package no.nav.arrangor.utils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

infix fun LocalDateTime.shouldBeCloseTo(expected: LocalDateTime?) {
    expected.shouldNotBeNull()
    this.shouldBeWithin(Duration.ofSeconds(10), expected)
}

fun LocalDateTime.shouldBeCloseToNow() = this.shouldBeWithin(Duration.ofSeconds(10), LocalDateTime.now())

infix fun ZonedDateTime.shouldBeCloseTo(expected: ZonedDateTime?) {
    expected.shouldNotBeNull()
    this.shouldBeWithin(Duration.ofSeconds(10), expected)
}
