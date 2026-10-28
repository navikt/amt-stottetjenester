package no.nav.amt.person.service.clients.pdl

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PdlAttributeExtensionsTest {
    @Nested
    inner class ToNavnMedFallbackTests {
        @Test
        fun `toNavnMedFallback - ingen navn - returnerer fallback navn`() {
            val nameAttributes = emptyList<PdlQueries.Attribute.Navn>()

            val navn = nameAttributes.toNavnMedFallback()

            assertSoftly(navn) {
                fornavn shouldBe "Ukjent"
                mellomnavn.shouldBeNull()
                etternavn shouldBe "Ukjent"
            }
        }

        @Test
        fun `toNavnMedFallback - ett navn - returnerer navn`() {
            val navnInTest =
                PdlQueries.Attribute.Navn(
                    fornavn = "Fornavn",
                    mellomnavn = "Mellomnavn",
                    etternavn = "Etternavn",
                )

            val navn = listOf(navnInTest).toNavnMedFallback()

            assertSoftly(navn) {
                fornavn shouldBe navnInTest.fornavn
                mellomnavn shouldBe navnInTest.mellomnavn
                etternavn shouldBe navnInTest.etternavn
            }
        }
    }
}
