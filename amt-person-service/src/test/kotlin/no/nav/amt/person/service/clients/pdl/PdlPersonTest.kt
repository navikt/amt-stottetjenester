package no.nav.amt.person.service.clients.pdl

import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Personident
import org.junit.jupiter.api.Test

class PdlPersonTest {
    @Test
    fun `toPersonDbo normaliserer navn fra PDL`() {
        // Arrange
        val pdlPerson = PdlPerson(
            erFalskIdentitet = false,
            fornavn = "FORNAVN",
            mellomnavn = "MELLOMNAVN",
            etternavn = "ETTERNAVN",
            telefonnummer = null,
            adressebeskyttelseGradering = null,
            identer = listOf(
                Personident(
                    ident = "test-ident",
                    historisk = false,
                    type = IdentType.FOLKEREGISTERIDENT,
                ),
            ),
            adresse = null,
        )

        // Act
        val person = pdlPerson.toPersonDbo()

        // Assert
        person.fornavn shouldBe "Fornavn"
        person.mellomnavn shouldBe "Mellomnavn"
        person.etternavn shouldBe "Etternavn"
    }
}
