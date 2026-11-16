package no.nav.amt.person.service.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.dbo.PersonDbo
import org.junit.jupiter.api.Test

class PersonUpdaterTest {
    private val personRepository: PersonRepository = mockk()
    private val personService: PersonService = mockk(relaxUnitFun = true)
    private val pdlClient: PdlClient = mockk()
    private val personUpdater = PersonUpdater(personRepository, personService, pdlClient)

    @Test
    fun `oppdaterer alle personnavn batchvis`() {
        val person1 = TestData.lagPerson()
        val person2 = TestData.lagPerson()
        val person3 = TestData.lagPerson()

        every { personRepository.getAll(0, 100) } returns listOf(person1, person2)
        every { personRepository.getAll(2, 100) } returns listOf(person3)
        every { personRepository.getAll(3, 100) } returns emptyList()
        every { personService.oppdaterNavnForBulk(any()) } returns Result.success(Unit)

        personUpdater.oppdaterAllePersonnavn()

        verify { personService.oppdaterNavnForBulk(person1) }
        verify { personService.oppdaterNavnForBulk(person2) }
        verify { personService.oppdaterNavnForBulk(person3) }
    }

    @Test
    fun `stopper og propagerer feil som ikke kommer fra PDL-kallet`() {
        val person1 = TestData.lagPerson()
        val person2 = TestData.lagPerson()
        val exception = IllegalStateException("Lagring feilet")

        every { personRepository.getAll(0, 100) } returns listOf(person1, person2)
        every { personService.oppdaterNavnForBulk(person1) } throws exception

        shouldThrow<IllegalStateException> {
            personUpdater.oppdaterAllePersonnavn()
        } shouldBe exception

        verify(exactly = 0) { personService.oppdaterNavnForBulk(person2) }
    }

    @Test
    fun `stanser når mer enn fem prosent av siste 500 PDL-kall feiler`() {
        val personer = List(1026) { TestData.lagPerson() }
        val feilendePersoner = personer.take(20).toSet() + personer.takeLast(26).toSet()

        every { personRepository.getAll(any(), 100) } answers {
            personer.drop(firstArg<Int>()).take(100)
        }
        every { personService.oppdaterNavnForBulk(any()) } answers {
            if (firstArg<PersonDbo>() in feilendePersoner) {
                Result.failure(IllegalStateException("PDL-feil"))
            } else {
                Result.success(Unit)
            }
        }

        val exception = shouldThrow<PdlFailureRateExceededException> {
            personUpdater.oppdaterAllePersonnavn()
        }

        exception.failures shouldBe 26
        exception.windowSize shouldBe 500
        verify(exactly = 0) { personRepository.getAll(1026, 100) }
    }

    @Test
    fun `fortsetter når feilraten er nøyaktig fem prosent`() {
        val personer = List(500) { TestData.lagPerson() }
        val feilendePersoner = personer.take(25).toSet()

        every { personRepository.getAll(any(), 100) } answers {
            personer.drop(firstArg<Int>()).take(100)
        }
        every { personService.oppdaterNavnForBulk(any()) } answers {
            if (firstArg<PersonDbo>() in feilendePersoner) {
                Result.failure(IllegalStateException("PDL-feil"))
            } else {
                Result.success(Unit)
            }
        }

        personUpdater.oppdaterAllePersonnavn()

        verify(exactly = 500) { personService.oppdaterNavnForBulk(any()) }
    }
}
