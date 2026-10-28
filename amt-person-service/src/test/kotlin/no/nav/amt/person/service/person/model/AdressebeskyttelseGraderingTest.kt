package no.nav.amt.person.service.person.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AdressebeskyttelseGraderingTest {
    @Test
    fun `erBeskyttet - ugradert - skal returnere false`() {
        AdressebeskyttelseGradering.UGRADERT.erBeskyttet() shouldBe false
    }

    @ParameterizedTest
    @EnumSource(AdressebeskyttelseGradering::class, names = ["FORTROLIG", "STRENGT_FORTROLIG", "STRENGT_FORTROLIG_UTLAND"])
    fun `erBeskyttet - er ikke null eller ugradert - skal returnere true`(gradering: AdressebeskyttelseGradering) {
        gradering.erBeskyttet() shouldBe true
    }
}
