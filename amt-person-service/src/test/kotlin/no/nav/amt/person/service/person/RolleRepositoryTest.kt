package no.nav.amt.person.service.person

import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.person.model.Rolle
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [RolleRepository::class])
class RolleRepositoryTest(
    private val rolleRepository: RolleRepository,
) : RepositoryTestBase() {
    @Test
    fun `insert - ny rolle - inserter rolle`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)
        rolleRepository.insert(person.id, Rolle.ARRANGOR_ANSATT)
        rolleRepository.insert(person.id, Rolle.NAV_BRUKER)

        rolleRepository.harRolle(person.id, Rolle.ARRANGOR_ANSATT) shouldBe true
        rolleRepository.harRolle(person.id, Rolle.NAV_BRUKER) shouldBe true
    }

    @Test
    fun `delete - rolle finnes - sletter rolle for person`() {
        val person = TestData.lagPerson()
        testDataRepository.insertPerson(person)
        testDataRepository.insertRolle(person.id, Rolle.NAV_BRUKER)

        rolleRepository.delete(person.id, Rolle.NAV_BRUKER)

        rolleRepository.harRolle(person.id, Rolle.NAV_BRUKER) shouldBe false
    }
}
