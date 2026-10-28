package no.nav.amt.person.service.extension

import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.utils.titlecase
import org.junit.jupiter.api.Test

class StringExtensionsTest {
    @Test
    fun `titlecase() - konverterer navn med full uppercasing til title case`() {
        val navn = "MITT NAVN"
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "Mitt Navn"
    }

    @Test
    fun `titlecase() - konverterer navn med full uppercasing og æ ø og å til title case`() {
        val navn = "ÅGE ØYVIND ÆGERØY"
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "Åge Øyvind Ægerøy"
    }

    @Test
    fun `titlecase() - konverterer navn med blandet casing til title case`() {
        val navn = "jOhN dOe"
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "John Doe"
    }

    @Test
    fun `titlecase() - konverterer navn med bindestrek til title case`() {
        val navn = "mary-jane parker"
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "Mary-Jane Parker"
    }

    @Test
    fun `titlecase() - konverterer navn med  karakter til title case`() {
        val navn = "O'SULLIVAN"
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "O'Sullivan"
    }

    @Test
    fun `titlecase() - konverterer navn med punktum til title case`() {
        val navn = "FOO BAR JR."
        val konvertertNavn = navn.titlecase()
        konvertertNavn shouldBe "Foo Bar Jr."
    }
}
