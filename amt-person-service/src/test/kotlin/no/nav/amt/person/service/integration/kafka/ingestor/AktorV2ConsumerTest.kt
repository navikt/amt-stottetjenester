package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.AktorV2Consumer
import no.nav.amt.person.service.kafka.producer.dto.NavBrukerDtoV1
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonidentRepository
import no.nav.amt.person.service.person.model.IdentType
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.aktor.v2.Identifikator
import no.nav.person.pdl.aktor.v2.Type
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AktorV2ConsumerTest(
    private val aktorV2Consumer: AktorV2Consumer,
    private val personidentRepository: PersonidentRepository,
    private val personRepository: PersonRepository,
) : IntegrationTestBase() {
    @Nested
    inner class Ingest {
        @Test
        fun `ny personident - oppdaterer person`() {
            // Arrange
            val navBruker = TestData.lagNavBruker()
            testDataRepository.insertNavBruker(navBruker)
            val person = navBruker.person
            val nyttFnr = TestData.randomIdent()
            val msg = Aktor(
                listOf(
                    Identifikator(nyttFnr, Type.FOLKEREGISTERIDENT, true),
                    Identifikator(person.personident, Type.FOLKEREGISTERIDENT, false),
                ),
            )

            // Act
            aktorV2Consumer.ingest("aktorId", msg)

            // Assert
            val faktiskPerson = personRepository.get(nyttFnr).shouldNotBeNull()
            val identer = personidentRepository.getAllForPerson(faktiskPerson.id)
            assertSoftly(identer.first { it.ident == person.personident }) {
                it.historisk shouldBe true
                it.type shouldBe IdentType.FOLKEREGISTERIDENT
            }
            val record = outboxRecords().single()
            val navBrukerRecord = objectMapper.readValue(record.value, NavBrukerDtoV1::class.java)
            record.key shouldBe person.id.toString()
            navBrukerRecord.personident shouldBe nyttFnr
        }

        @Test
        fun `bruker far flere gjeldende identer - skal lagre folkeregisterident`() {
            // Arrange
            val person = TestData.lagPerson()
            testDataRepository.insertPerson(person)
            val nyttFnr = TestData.randomIdent()
            val aktorId = TestData.randomIdent()
            val msg = Aktor(
                listOf(
                    Identifikator(aktorId, Type.AKTORID, true),
                    Identifikator(nyttFnr, Type.FOLKEREGISTERIDENT, true),
                    Identifikator(person.personident, Type.FOLKEREGISTERIDENT, false),
                ),
            )

            // Act
            aktorV2Consumer.ingest("aktorId", msg)

            // Assert
            val faktiskPerson = personRepository.get(nyttFnr).shouldNotBeNull()
            faktiskPerson.personident shouldBe nyttFnr
            val identer = personidentRepository.getAllForPerson(faktiskPerson.id)
            identer shouldHaveSize 3
            identer.first { it.ident == person.personident }.historisk shouldBe true
        }
    }
}
