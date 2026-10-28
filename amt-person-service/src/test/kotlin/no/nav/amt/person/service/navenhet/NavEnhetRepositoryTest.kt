package no.nav.amt.person.service.navenhet

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.utils.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest(classes = [NavEnhetRepository::class])
class NavEnhetRepositoryTest(
    private val enhetRepository: NavEnhetRepository,
) : RepositoryTestBase() {
    @Test
    fun `get(uuid) - enhet finnes - returnerer enhet`() {
        val enhet = TestData.lagNavEnhet()
        testDataRepository.insertNavEnhet(enhet)

        val faktiskEnhet = enhetRepository.get(enhet.id)

        assertSoftly(faktiskEnhet) {
            id shouldBe enhet.id
            enhetId shouldBe enhet.enhetId
            navn shouldBe enhet.navn
            createdAt shouldBeEqualTo enhet.createdAt
            modifiedAt shouldBeEqualTo enhet.modifiedAt
        }
    }

    @Test
    fun `get(uuid) - enhet finnes ikke - kaster NoSuchElementException`() {
        shouldThrow<NoSuchElementException> {
            enhetRepository.get(UUID.randomUUID())
        }
    }

    @Test
    fun `get(enhetId) - enhet finnes - returnerer enhet`() {
        val enhet = TestData.lagNavEnhet()
        testDataRepository.insertNavEnhet(enhet)

        val faktiskEnhet = enhetRepository.get(enhet.enhetId)
        assertSoftly(faktiskEnhet.shouldNotBeNull()) {
            id shouldBe enhet.id
            enhetId shouldBe enhet.enhetId
            navn shouldBe enhet.navn
            createdAt shouldBeEqualTo enhet.createdAt
            modifiedAt shouldBeEqualTo enhet.modifiedAt
        }
    }

    @Test
    fun `get(enhetId) - enhet finnes ikke - returnerer null`() {
        enhetRepository.get("Ikke Eksisternede Enhet") shouldBe null
    }

    @Test
    fun `insert - ny enhet - inserter ny enhet`() {
        val enhet = NavEnhetDbo(
            id = UUID.randomUUID(),
            enhetId = "0001",
            navn = "Ny Nav Enhet",
        )

        enhetRepository.insert(enhet)

        val faktiskEnhet = enhetRepository.get(enhet.id)

        assertSoftly(faktiskEnhet) {
            id shouldBe enhet.id
            enhetId shouldBe enhet.enhetId
            navn shouldBe enhet.navn
        }
    }

    @Test
    fun `getAll - ingen enheter - returnerer tom liste`() {
        val enheter = enhetRepository.getAll()
        enheter.shouldBeEmpty()
    }

    @Test
    fun `getAll - flere enheter - returnerer flere enheter`() {
        val enhet1 = TestData.lagNavEnhet()
        val enhet2 = TestData.lagNavEnhet()

        testDataRepository.insertNavEnhet(enhet1)
        testDataRepository.insertNavEnhet(enhet2)

        val enheter = enhetRepository.getAll()

        enheter.map { it.id } shouldContainAll listOf(enhet1.id, enhet2.id)
    }

    @Test
    fun `update - enhet finnes - oppdaterer enhet`() {
        val enhet = TestData.lagNavEnhet()
        testDataRepository.insertNavEnhet(enhet)

        val oppdatertEnhet = enhet.copy(navn = "Nytt Navn")

        enhetRepository.update(oppdatertEnhet)

        val faktiskEnhet = enhetRepository.get(enhet.id)

        faktiskEnhet.navn shouldBe "Nytt Navn"
        faktiskEnhet.modifiedAt shouldNotBe enhet.modifiedAt
    }
}
