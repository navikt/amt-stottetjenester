package no.nav.amt.person.service.navansatt

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.utils.shouldBeCloseTo
import no.nav.amt.person.service.utils.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [NavAnsattRepository::class])
class NavAnsattRepositoryTest(
    private val ansattRepository: NavAnsattRepository,
) : RepositoryTestBase() {
    @Test
    fun `get(uuid) - ansatt finnes - returnerer ansatt`() {
        val ansatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(ansatt)

        val faktiskAnsatt = ansattRepository.get(ansatt.id)

        assertSoftly(faktiskAnsatt) {
            id shouldBe ansatt.id
            navIdent shouldBe ansatt.navIdent
            navn shouldBe ansatt.navn
            telefon shouldBe ansatt.telefon
            epost shouldBe ansatt.epost
            createdAt shouldBeEqualTo ansatt.createdAt
            modifiedAt shouldBeEqualTo ansatt.modifiedAt
        }
    }

    @Test
    fun `get(uuid) - ansatt finnes ikke - kaster NoSuchElementException`() {
        shouldThrow<NoSuchElementException> {
            ansattRepository.get(UUID.randomUUID())
        }
    }

    @Test
    fun `get(navIdent) - ansatt finnes - returnerer ansatt`() {
        val ansatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(ansatt)

        val faktiskAnsatt = ansattRepository.get(ansatt.navIdent)

        assertSoftly(faktiskAnsatt.shouldNotBeNull()) {
            id shouldBe ansatt.id
            navIdent shouldBe ansatt.navIdent
            navn shouldBe ansatt.navn
            telefon shouldBe ansatt.telefon
            epost shouldBe ansatt.epost
            createdAt shouldBeEqualTo ansatt.createdAt
            modifiedAt shouldBeEqualTo ansatt.modifiedAt
        }
    }

    @Test
    fun `get(navIdent) - ansatt finnes ikke - returnerer null`() {
        ansattRepository.get("Ukjent Ident") shouldBe null
    }

    @Test
    fun `upsert - ansatt finnes ikke - oppretter ny ansatt`() {
        val ansatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavGrunerlokka()

        ansattRepository.upsert(ansatt)

        val faktiskAnsatt = ansattRepository.get(ansatt.navIdent)
        assertSoftly(faktiskAnsatt.shouldNotBeNull()) {
            id shouldBe ansatt.id
            navIdent shouldBe ansatt.navIdent
            navn shouldBe ansatt.navn
            telefon shouldBe ansatt.telefon
            epost shouldBe ansatt.epost
        }
    }

    @Test
    fun `upsert - ansatt finnes - oppdatterer ansatt`() {
        val ansatt = TestData.lagNavAnsatt(
            createdAt = LocalDateTime.now().minusMonths(6),
            modifiedAt = LocalDateTime.now().minusMonths(6),
        )

        val oppdatertAnsatt = NavAnsattDbo(
            id = ansatt.id,
            navIdent = ansatt.navIdent,
            navn = "Nytt Navn",
            epost = "ny_epost@nav.no",
            telefon = "12345678",
            navEnhetId = null,
        )

        testDataRepository.insertNavAnsatt(ansatt)

        ansattRepository.upsert(oppdatertAnsatt)

        val faktiskAnsatt = ansattRepository.get(ansatt.id)

        assertSoftly(faktiskAnsatt) {
            id shouldBe ansatt.id
            navIdent shouldBe ansatt.navIdent
            navn shouldBe oppdatertAnsatt.navn
            telefon shouldBe oppdatertAnsatt.telefon
            epost shouldBe oppdatertAnsatt.epost
            createdAt shouldBeEqualTo ansatt.createdAt
            modifiedAt shouldBeCloseTo LocalDateTime.now()
        }
    }

    @Test
    fun `getAll - ansatte finnes - returnerer liste med ansatte`() {
        val ansatt = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(ansatt)

        val alleAnsatte = ansattRepository.getAll()

        alleAnsatte shouldHaveAtLeastSize 1
        alleAnsatte.firstOrNull { it.id == ansatt.id } shouldNotBe null
    }

    @Test
    fun `upsertMany - flere ansatte - oppdaterer ansatte`() {
        val ansatt1 = TestData.lagNavAnsatt()
        val ansatt2 = TestData.lagNavAnsatt()
        testDataRepository.insertNavAnsatt(ansatt1)
        testDataRepository.insertNavAnsatt(ansatt2)

        val oppdatertAnsatt1 = NavAnsattDbo(
            id = ansatt1.id,
            navIdent = ansatt1.navIdent,
            navn = "nytt navn 1",
            telefon = ansatt1.telefon,
            epost = ansatt1.epost,
            navEnhetId = null,
        )

        val oppdatertAnsatt2 = NavAnsattDbo(
            id = ansatt2.id,
            navIdent = ansatt2.navIdent,
            navn = "nytt navn 2",
            telefon = ansatt2.telefon,
            epost = ansatt2.epost,
            navEnhetId = null,
        )

        ansattRepository.upsertMany(setOf(oppdatertAnsatt1, oppdatertAnsatt2))

        assertSoftly(ansattRepository.get(oppdatertAnsatt1.navIdent).shouldNotBeNull()) {
            navn shouldBe oppdatertAnsatt1.navn
        }

        assertSoftly(ansattRepository.get(oppdatertAnsatt2.navIdent).shouldNotBeNull()) {
            navn shouldBe oppdatertAnsatt2.navn
        }
    }
}
