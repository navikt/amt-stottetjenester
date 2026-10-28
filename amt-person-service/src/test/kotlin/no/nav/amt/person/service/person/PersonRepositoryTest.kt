package no.nav.amt.person.service.person

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.shouldBeCloseTo
import no.nav.amt.person.service.utils.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [PersonRepository::class])
class PersonRepositoryTest(
    private val personRepository: PersonRepository,
) : RepositoryTestBase() {
    @Test
    fun `get(uuid) - person finnes - returnerer person`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)

        val faktiskPerson = personRepository.get(person.id)

        assertSoftly(faktiskPerson) {
            id shouldBe person.id
            personident shouldBe person.personident
            fornavn shouldBe person.fornavn
            mellomnavn shouldBe person.mellomnavn
            etternavn shouldBe person.etternavn
            createdAt shouldBeEqualTo person.createdAt
            modifiedAt shouldBeEqualTo person.modifiedAt
        }
    }

    @Test
    fun `get(uuid) - person finnes ikke - kaster NoSuchElementException`() {
        shouldThrow<NoSuchElementException> {
            personRepository.get(UUID.randomUUID())
        }
    }

    @Test
    fun `get(personident) - person finnes ikke - returnerer null`() {
        personRepository.get(TestData.randomIdent()) shouldBe null
    }

    @Test
    fun `getPersoner - flere personer, en finnes ikke - returnerer personer som finnes fra før`() {
        val person1 = TestData.lagPerson()
        val person2 = TestData.lagPerson()
        testDataRepository.insertPerson(person1)
        testDataRepository.insertPerson(person2)

        val personer = personRepository.getPersoner(
            setOf(
                person1.personident,
                person2.personident,
                TestData.randomIdent(),
            ),
        )

        personer.map { it.id } shouldContainAll listOf(person1.id, person2.id)
    }

    @Test
    fun `getPersoner - person med historisk ident - returnerer liste`() {
        val person = TestData.lagPerson()
        val historiskIdent = TestData.lagPersonident(personId = person.id, historisk = true)

        testDataRepository.insertPerson(person)
        testDataRepository.insertPersonidenter(listOf(historiskIdent))

        val personer = personRepository.getPersoner(setOf(historiskIdent.ident, person.personident))

        personer shouldHaveSize 1

        val faktiskPerson = personer.first()

        faktiskPerson.id shouldBe person.id
        faktiskPerson.personident shouldNotBe historiskIdent.ident
    }

    @Test
    fun `getPersoner - ingen person med ident - returnerer tom liste`() {
        personRepository
            .getPersoner(
                setOf(TestData.randomIdent(), TestData.randomIdent()),
            ).shouldBeEmpty()
    }

    @Test
    fun `upsert - ny person - inserter person`() {
        val person = PersonDbo(
            id = UUID.randomUUID(),
            personident = TestData.randomIdent(),
            erFalskIdentitet = false,
            fornavn = "Fornavn",
            mellomnavn = "Mellomnavn",
            etternavn = "Etternavn",
        )

        personRepository.upsert(person)

        val faktiskPerson = personRepository.get(person.id)

        assertSoftly(faktiskPerson) {
            id shouldBe person.id
            personident shouldBe person.personident
            fornavn shouldBe person.fornavn
            mellomnavn shouldBe person.mellomnavn
            etternavn shouldBe person.etternavn
        }
    }

    @Test
    fun `upsert - eksisterende person - oppdaterer person`() {
        val originalPerson = TestData.lagPerson(
            createdAt = LocalDateTime.now().minusMonths(6),
            modifiedAt = LocalDateTime.now().minusMonths(6),
        )

        testDataRepository.insertPerson(originalPerson)

        val oppdatertPerson = PersonDbo(
            id = originalPerson.id,
            personident = originalPerson.personident,
            erFalskIdentitet = false,
            fornavn = "Nytt",
            mellomnavn = "Navn",
            etternavn = "Med Mer",
        )

        personRepository.upsert(oppdatertPerson)

        val faktiskPerson = personRepository.get(originalPerson.id)

        assertSoftly(faktiskPerson) {
            id shouldBe originalPerson.id
            personident shouldBe originalPerson.personident
            fornavn shouldBe oppdatertPerson.fornavn
            mellomnavn shouldBe oppdatertPerson.mellomnavn
            etternavn shouldBe oppdatertPerson.etternavn
            createdAt shouldBeEqualTo originalPerson.createdAt
            modifiedAt shouldBeCloseTo LocalDateTime.now()
        }
    }

    @Test
    fun `upsert - ny ident - oppdaterer person`() {
        val originalPerson = TestData.lagPerson(
            createdAt = LocalDateTime.now().minusMonths(6),
            modifiedAt = LocalDateTime.now().minusMonths(6),
        )

        testDataRepository.insertPerson(originalPerson)

        val oppdatertPerson = PersonDbo(
            id = originalPerson.id,
            personident = "ny ident",
            erFalskIdentitet = false,
            fornavn = "Nytt",
            mellomnavn = "Navn",
            etternavn = "Med Mer",
        )

        personRepository.upsert(oppdatertPerson)

        val faktiskPerson = personRepository.get(originalPerson.id)

        assertSoftly(faktiskPerson) {
            id shouldBe originalPerson.id
            personident shouldBe oppdatertPerson.personident
            fornavn shouldBe oppdatertPerson.fornavn
            mellomnavn shouldBe oppdatertPerson.mellomnavn
            etternavn shouldBe oppdatertPerson.etternavn
            createdAt shouldBeEqualTo originalPerson.createdAt
            modifiedAt shouldBeCloseTo LocalDateTime.now()
        }
    }

    @Test
    fun `delete - person finnes - sletter person()`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)

        personRepository.delete(person.id)

        personRepository.get(person.personident) shouldBe null
    }

    @Test
    fun `getAllWithRolle - arrangoransatt og nav-bruker finnes - returner kunn de med riktig rolle`() {
        val antallNavBrukere = 7
        val antallArrangorAnsatte = 3

        repeat(antallNavBrukere) { testDataRepository.insertNavBruker(TestData.lagNavBruker()) }
        repeat(antallArrangorAnsatte) {
            val person = TestData.lagPerson()
            testDataRepository.insertPerson(person)
            testDataRepository.insertRolle(person.id, Rolle.ARRANGOR_ANSATT)
        }

        val navBrukere = personRepository.getAllWithRolle(0, rolle = Rolle.NAV_BRUKER)
        navBrukere shouldHaveSize antallNavBrukere

        val arrangorAnsatte = personRepository.getAllWithRolle(0, rolle = Rolle.ARRANGOR_ANSATT)
        arrangorAnsatte shouldHaveSize antallArrangorAnsatte
    }
}
