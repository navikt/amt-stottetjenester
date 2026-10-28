package no.nav.amt.person.service.utils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.LocalDateTime

/**
 * Should be used to check equality, 1-second skew is allowed to work around different precision on milliseconds
 */
infix fun LocalDateTime.shouldBeEqualTo(expected: LocalDateTime?) {
    expected shouldNotBe null
    expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(1), this)
}

infix fun LocalDateTime.shouldBeCloseTo(expected: LocalDateTime?) {
    expected shouldNotBe null
    expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(10), this)
}
