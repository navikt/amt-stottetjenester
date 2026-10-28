package no.nav.arrangor.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OrgnummerTest {
    @Nested
    inner class ErGyldigTests {
        @Test
        fun `9 siffer som starter med 1 (hack for utenlandske organisasjoner) - true`() {
            Orgnummer.erGyldig("112345678") shouldBe true
        }

        @Test
        fun `9 siffer som starter med 8 - true`() {
            Orgnummer.erGyldig("812345678") shouldBe true
        }

        @Test
        fun `9 siffer som starter med 9 - true`() {
            Orgnummer.erGyldig("912345678") shouldBe true
        }

        @Test
        fun `9 siffer som starter med annet enn 8 eller 9 - false`() {
            Orgnummer.erGyldig("223456789") shouldBe false
            Orgnummer.erGyldig("712345678") shouldBe false
        }

        @Test
        fun `farre enn 9 siffer - false`() {
            Orgnummer.erGyldig("81234567") shouldBe false
        }

        @Test
        fun `flere enn 9 siffer - false`() {
            Orgnummer.erGyldig("8123456789") shouldBe false
        }

        @Test
        fun `bokstaver - false`() {
            Orgnummer.erGyldig("81234abcd") shouldBe false
        }

        @Test
        fun `whitespace - false`() {
            Orgnummer.erGyldig(" 812345678") shouldBe false
        }

        @Test
        fun `tom streng - false`() {
            Orgnummer.erGyldig("") shouldBe false
        }
    }
}
