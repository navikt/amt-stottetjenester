package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.client.enhetsregister.Virksomhet
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrangorServiceTest(
    private val arrangorService: ArrangorService,
    private val arrangorRepository: ArrangorRepository,
) : IntegrationTest() {
    @Test
    fun `getOrCreate(string) - overordnet arrangor mangler navn - oppretter ikke overordnet arrangor`() {
        val virksomhet =
            Virksomhet(
                organisasjonsnummer = randomOrgnr(),
                navn = "Foo",
                overordnetEnhetOrganisasjonsnummer = randomOrgnr(),
                overordnetEnhetNavn = null,
            )
        mockVirksomhet(virksomhet)

        val arrangor = arrangorService.getOrCreate(virksomhet.organisasjonsnummer)
        arrangor.navn shouldBe virksomhet.navn
        arrangor.organisasjonsnummer shouldBe virksomhet.organisasjonsnummer
        arrangor.overordnetArrangorId shouldBe null
    }

    @Test
    fun `getOrCreate(string) - har ikke overordnet enhet - oppretter ikke overordnet arrangor`() {
        val virksomhet =
            Virksomhet(
                organisasjonsnummer = randomOrgnr(),
                navn = "Foo",
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null,
            )
        mockVirksomhet(virksomhet)

        val arrangor = arrangorService.getOrCreate(virksomhet.organisasjonsnummer)
        arrangor.navn shouldBe virksomhet.navn
        arrangor.organisasjonsnummer shouldBe virksomhet.organisasjonsnummer
        arrangor.overordnetArrangorId shouldBe null
    }

    @Test
    fun `getOrCreate(string) - har overordnet enhet - oppretter overordnet arrangor`() {
        val virksomhet =
            Virksomhet(
                organisasjonsnummer = randomOrgnr(),
                navn = "Foo",
                overordnetEnhetOrganisasjonsnummer = randomOrgnr(),
                overordnetEnhetNavn = "Bar",
            )
        mockVirksomhet(virksomhet)

        val arrangor = arrangorService.getOrCreate(virksomhet.organisasjonsnummer)
        arrangor.navn shouldBe virksomhet.navn
        arrangor.organisasjonsnummer shouldBe virksomhet.organisasjonsnummer
        arrangor.overordnetArrangorId shouldNotBe null

        val overordnetArrangor = arrangorRepository.get(virksomhet.overordnetEnhetOrganisasjonsnummer!!)!!
        overordnetArrangor.id shouldBe arrangor.overordnetArrangorId
        overordnetArrangor.navn shouldBe virksomhet.overordnetEnhetNavn
        overordnetArrangor.organisasjonsnummer shouldBe virksomhet.overordnetEnhetOrganisasjonsnummer
        overordnetArrangor.overordnetArrangorId shouldBe null
    }

    @Test
    fun `getOrCreate(list) - en enhet er ikke opprettet - oppretter manglende arrangor og returnerer liste`() {
        val manglendeArrangor =
            Virksomhet(
                organisasjonsnummer = randomOrgnr(),
                navn = "Foo",
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null,
            )
        val eksisterendeArrangor =
            ArrangorRepository.ArrangorDbo(
                id = UUID.randomUUID(),
                navn = "Bar",
                organisasjonsnummer = randomOrgnr(),
                overordnetArrangorId = null,
            )
        arrangorRepository.insertOrUpdate(eksisterendeArrangor)
        mockVirksomhet(manglendeArrangor)

        val arrangorer =
            arrangorService.getOrCreate(listOf(manglendeArrangor.organisasjonsnummer, eksisterendeArrangor.organisasjonsnummer))
        arrangorer.size shouldBe 2

        val opprettetArrangor = arrangorer.first { it.organisasjonsnummer == manglendeArrangor.organisasjonsnummer }
        opprettetArrangor.navn shouldBe manglendeArrangor.navn
        opprettetArrangor.organisasjonsnummer shouldBe manglendeArrangor.organisasjonsnummer
        opprettetArrangor.overordnetArrangorId shouldBe null

        val eksisterende = arrangorer.first { it.organisasjonsnummer == eksisterendeArrangor.organisasjonsnummer }
        eksisterende.navn shouldBe eksisterendeArrangor.navn
        eksisterende.organisasjonsnummer shouldBe eksisterendeArrangor.organisasjonsnummer
        eksisterende.overordnetArrangorId shouldBe eksisterendeArrangor.overordnetArrangorId
    }

    private fun randomOrgnr() = (900_000_000..999_999_998).random().toString()
}
