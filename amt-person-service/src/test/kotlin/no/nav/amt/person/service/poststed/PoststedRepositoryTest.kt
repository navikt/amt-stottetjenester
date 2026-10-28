package no.nav.amt.person.service.poststed

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.amt.person.service.data.TestData.postnumreInTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest(classes = [PoststedRepository::class])
class PoststedRepositoryTest(
    private val sut: PoststedRepository,
) : RepositoryTestBase() {
    @BeforeEach
    fun before() = sut.oppdaterPoststed(postnumreInTest, UUID.randomUUID())

    @Test
    fun `getAllePoststeder - poststeder finnes - henter alle poststeder`() {
        val allePoststeder = sut.getAllePoststeder()

        allePoststeder shouldContainAll postnumreInTest
    }

    @Nested
    inner class GetPostederTests {
        @Test
        fun `getPoststeder - postnummer finnes - returnerer poststeder`() {
            val postnumreToFetch = setOf("0484", "5341")
            val poststeder = sut.getPoststeder(postnumreToFetch)

            poststeder.map { it.postnummer } shouldContainAll postnumreToFetch
        }

        @Test
        fun `getPoststeder - postnummer finnes ikke - returnerer poststeder`() {
            val postnumreToFetch = setOf("0484", "9999")
            val poststeder = sut.getPoststeder(postnumreToFetch)

            poststeder.size shouldBe 1
        }
    }

    @Nested
    inner class OppdaterPoststedTests {
        @Test
        fun `oppdaterPoststed - postnummer finnes i db men ikke i oppdatert liste - sletter poststed`() {
            val postnrUtenStraume = postnumreInTest.filterNot { it.postnummer == "5341" }.toSet()

            sut.oppdaterPoststed(
                oppdatertePostnummer = postnrUtenStraume,
                sporingsId = UUID.randomUUID(),
            )

            val allePoststeder = sut.getAllePoststeder()

            allePoststeder.size shouldBe 4
            allePoststeder.find { it.postnummer == "5341" } shouldBe null
        }

        @Test
        fun `oppdaterPoststed - postnummer finnes i oppdatert liste men ikke i db - legger til poststed`() {
            val nyttPostnummer = Postnummer("4567", "ASKER")

            sut.oppdaterPoststed(
                postnumreInTest.plus(nyttPostnummer),
                UUID.randomUUID(),
            )

            val allePoststeder = sut.getAllePoststeder()

            allePoststeder.size shouldBe 6
            allePoststeder shouldContain nyttPostnummer
        }

        @Test
        fun `oppdaterPoststed - flere endringer - sletter 1 poststed, lagrer 1 poststed, bytter navn for 1 poststed`() {
            val oppdatertePostnumre = setOf(
                Postnummer("0484", "OSLO"),
                Postnummer("0502", "OSLO"),
                Postnummer("5341", "STRAUME"),
                Postnummer("5365", "TURØY"),
                Postnummer("9609", "SENJA"),
            )

            sut.oppdaterPoststed(
                oppdatertePostnumre,
                UUID.randomUUID(),
            )

            val allePoststeder = sut.getAllePoststeder()

            allePoststeder.size shouldBe 5
            allePoststeder shouldContainAll oppdatertePostnumre
        }

        @Test
        fun `oppdaterPoststed - ingen endringer - oppdaterer ingenting`() {
            val allePoststeder = sut.getAllePoststeder()

            sut.oppdaterPoststed(
                postnumreInTest,
                UUID.randomUUID(),
            )

            val allePoststederOppdatert = sut.getAllePoststeder()
            allePoststederOppdatert shouldBe allePoststeder
        }
    }
}
