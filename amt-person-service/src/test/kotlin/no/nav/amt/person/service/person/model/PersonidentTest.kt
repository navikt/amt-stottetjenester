package no.nav.amt.person.service.person.model

import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.junit.jupiter.api.Test

class PersonidentTest {
    @Test
    fun `finnGjeldendeIdent - flere typer gjeldende identer - fregident er gjeldende`() {
        val forventetIdent = TestData.lagPersonident(historisk = false, type = IdentType.FOLKEREGISTERIDENT)
        val identer = listOf(
            forventetIdent,
            TestData.lagPersonident(historisk = true, type = IdentType.FOLKEREGISTERIDENT),
            TestData.lagPersonident(historisk = false, type = IdentType.NPID),
            TestData.lagPersonident(historisk = false, type = IdentType.AKTORID),
        ).map {
            Personident(
                ident = it.ident,
                historisk = it.historisk,
                type = it.type,
            )
        }

        identer.finnGjeldendeIdent().getOrThrow() shouldBe
            Personident(
                ident = forventetIdent.ident,
                historisk = forventetIdent.historisk,
                type = forventetIdent.type,
            )
    }

    @Test
    fun `finnGjeldendeIdent - ingen fregident - npid er gjeldende`() {
        val forventetIdent = TestData.lagPersonident(historisk = false, type = IdentType.NPID)
        val identer = listOf(
            TestData.lagPersonident(historisk = false, type = IdentType.AKTORID),
            forventetIdent,
        ).map {
            Personident(
                ident = it.ident,
                historisk = it.historisk,
                type = it.type,
            )
        }

        identer.finnGjeldendeIdent().getOrThrow() shouldBe
            Personident(
                ident = forventetIdent.ident,
                historisk = forventetIdent.historisk,
                type = forventetIdent.type,
            )
    }

    @Test
    fun `finnGjeldendeIdent - kun aktorid - returner failure`() {
        val identer = listOf(
            TestData.lagPersonident(historisk = false, type = IdentType.AKTORID),
        ).map {
            Personident(
                ident = it.ident,
                historisk = it.historisk,
                type = it.type,
            )
        }

        identer.finnGjeldendeIdent().isFailure shouldBe true
    }
}
