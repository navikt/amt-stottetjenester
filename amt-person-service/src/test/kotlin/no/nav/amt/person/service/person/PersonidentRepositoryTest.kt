package no.nav.amt.person.service.person

import io.kotest.matchers.collections.shouldContainAll
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.TestData.lagPersonident
import no.nav.amt.person.service.person.model.IdentType
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [PersonidentRepository::class])
class PersonidentRepositoryTest(
    private val personidentRepository: PersonidentRepository,
) : RepositoryTestBase() {
    @Test
    fun `upsertPersonident - nye identer - inserter`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)

        val identer = setOf(
            lagPersonident(personId = person.id, historisk = true, type = IdentType.AKTORID),
            lagPersonident(personId = person.id, historisk = true, type = IdentType.NPID),
        )

        personidentRepository.upsert(identer)

        val faktiskeIdenter = personidentRepository.getAllForPerson(person.id)

        faktiskeIdenter.map { it.ident } shouldContainAll identer.map { it.ident }
    }

    @Test
    fun `upsertPersonident - ny ident - inserter og oppdaterer`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)

        val identer = setOf(
            lagPersonident(personId = person.id, historisk = true),
            lagPersonident(personId = person.id, historisk = false),
        )

        personidentRepository.upsert(identer)

        val faktiskeIdenter = personidentRepository.getAllForPerson(person.id)

        faktiskeIdenter.map { it.ident } shouldContainAll identer.map { it.ident }
    }
}
