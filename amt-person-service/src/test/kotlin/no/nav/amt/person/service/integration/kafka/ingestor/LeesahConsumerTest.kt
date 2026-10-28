package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.kafka.KafkaMessageCreator
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.LeesahConsumer
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.utils.titlecase
import no.nav.person.pdl.leesah.adressebeskyttelse.Gradering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LeesahConsumerTest(
    private val leesahConsumer: LeesahConsumer,
    private val personRepository: PersonRepository,
    private val navBrukerRepository: NavBrukerRepository,
) : IntegrationTestBase() {
    @Nested
    inner class Navn {
        @Test
        fun `oppdaterer navn på person`() {
            // Arrange
            val person = TestData.lagPerson()
            val navBruker = TestData.lagNavBruker(person = person)
            testDataRepository.insertNavBruker(navBruker)

            val nyttFornavn = "NYTT FORNAVN"
            val nyttMellomnavn = "NYTT MELLOMNAVN"
            val nyttEtternavn = "NYTT ETTERNAVN"

            every { pdlClient.hentTelefon(person.personident) } returns null
            every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(
                person.copy(
                    fornavn = nyttFornavn,
                    mellomnavn = nyttMellomnavn,
                    etternavn = nyttEtternavn,
                ),
            )

            val personhendelse = KafkaMessageCreator.lagPersonhendelseNavn(
                personidenter = listOf(person.personident),
                fornavn = nyttFornavn,
                mellomnavn = nyttMellomnavn,
                etternavn = nyttEtternavn,
            )

            // Act
            leesahConsumer.ingest(personhendelse)

            // Assert
            assertSoftly(personRepository.get(person.id)) {
                fornavn shouldBe nyttFornavn.titlecase()
                mellomnavn shouldBe nyttMellomnavn.titlecase()
                etternavn shouldBe nyttEtternavn.titlecase()
            }
        }
    }

    @Nested
    inner class Adressebeskyttelse {
        @Test
        fun `oppdaterer adressebeskyttelse og fjerner adresse`() {
            // Arrange
            val navBruker = TestData.lagNavBruker(adresse = TestData.lagAdresse())
            testDataRepository.insertNavBruker(navBruker)

            every { pdlClient.hentPerson(navBruker.person.personident) } returns TestData.lagPdlPerson(
                navBruker.person,
                adressebeskyttelseGradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
                adresse = navBruker.adresse,
            )

            val personhendelse = KafkaMessageCreator.lagPersonhendelseAdressebeskyttelse(
                personidenter = listOf(navBruker.person.personident),
                gradering = Gradering.STRENGT_FORTROLIG,
            )

            // Act
            leesahConsumer.ingest(personhendelse)

            // Assert
            val oppdatertNavBruker = navBrukerRepository.get(navBruker.person.personident)
            oppdatertNavBruker?.adressebeskyttelse shouldBe no.nav.amt.person.service.navbruker.Adressebeskyttelse.STRENGT_FORTROLIG
            oppdatertNavBruker?.adresse shouldBe null
        }
    }

    @Nested
    inner class FalskIdentitet {
        @Test
        fun `oppdaterer erFalskIdentitet på person`() {
            // Arrange
            val person = TestData.lagPerson(erFalskIdentitet = false)
            val navBruker = TestData.lagNavBruker(person = person)
            testDataRepository.insertNavBruker(navBruker)

            every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(
                person.copy(erFalskIdentitet = true),
            )

            val personhendelse = KafkaMessageCreator.lagPersonhendelseFalskIdentitet(
                personidenter = listOf(person.personident),
            )

            // Act
            leesahConsumer.ingest(personhendelse)

            // Assert
            personRepository.get(person.id).erFalskIdentitet shouldBe true
        }
    }
}
