package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.arrangor.ArrangorRepository.ArrangorDbo
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrangorRepositoryTest(
    private val arrangorRepository: ArrangorRepository,
) : RepositoryTestBase() {
    @Test
    fun `insertOrUpdate - arrangor finnes ikke - inserter og returnerer arrangor`() {
        val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor) shouldBe arrangor
        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `insertOrUpdate - arrangor finnes - oppdaterer og returnerer arrangor`() {
        val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor)
        val arrangorOppdatert = arrangor.copy(navn = "Bar")

        arrangorRepository.insertOrUpdate(arrangorOppdatert) shouldBe arrangorOppdatert
        arrangorRepository.get(arrangor.id) shouldBe arrangorOppdatert
    }

    @Test
    fun `get(uuid) - arrangor finnes ikke - returnerer null`() {
        arrangorRepository.get(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `get(uuid) - arrangor finnes - returnerer arrangor`() {
        val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor)
        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `get(string) - arrangor finnes ikke - returnerer null`() {
        arrangorRepository.get("Foobar") shouldBe null
    }

    @Test
    fun `get(string) - arrangor finnes - returnerer arrangor`() {
        val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor)
        arrangorRepository.get(arrangor.organisasjonsnummer) shouldBe arrangor
    }

    @Test
    fun `getArrangorerMedIder - arrangorer finnes ikke - returnerer tom liste`() {
        arrangorRepository.getArrangorerMedIder(listOf(UUID.randomUUID())) shouldBe emptyList()
    }

    @Test
    fun `getArrangorerMedIder - arrangorer finnes - returnerer liste`() {
        val arrangor1 = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        val arrangor2 = ArrangorDbo(UUID.randomUUID(), "Bar", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor1)
        arrangorRepository.insertOrUpdate(arrangor2)

        arrangorRepository.getArrangorerMedIder(listOf(arrangor1.id, arrangor2.id)) shouldBe listOf(arrangor1, arrangor2)
    }

    @Test
    fun `getArrangorerMedOrgnumre - arrangorer finnes ikke - returnerer tom liste`() {
        arrangorRepository.getArrangorerMedOrgnumre(listOf("Foobar")) shouldBe emptyList()
    }

    @Test
    fun `getArrangorerMedOrgnumre - arrangorer finnes - returnerer liste`() {
        val arrangor1 = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
        val arrangor2 = ArrangorDbo(UUID.randomUUID(), "Bar", randomOrgnr(), null)
        arrangorRepository.insertOrUpdate(arrangor1)
        arrangorRepository.insertOrUpdate(arrangor2)

        arrangorRepository.getArrangorerMedOrgnumre(
            listOf(
                arrangor1.organisasjonsnummer,
                arrangor2.organisasjonsnummer,
            ),
        ) shouldBe listOf(arrangor1, arrangor2)
    }

    private fun randomOrgnr() = (900_000_000..999_999_998).random().toString()
}
