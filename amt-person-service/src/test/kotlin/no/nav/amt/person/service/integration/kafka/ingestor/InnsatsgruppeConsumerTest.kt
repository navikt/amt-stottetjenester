package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.InnsatsgruppeConsumer
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Personident
import org.junit.jupiter.api.Test

class InnsatsgruppeConsumerTest(
    private val innsatsgruppeConsumer: InnsatsgruppeConsumer,
    private val navBrukerRepository: NavBrukerRepository,
) : IntegrationTestBase() {
    @Test
    fun `ingest - bruker finnes, ny innsatsgruppe - oppdaterer`() {
        val navBruker = TestData.lagNavBruker(innsatsgruppe = InnsatsgruppeV1.STANDARD_INNSATS)
        testDataRepository.insertNavBruker(navBruker)

        val siste14aVedtak = InnsatsgruppeConsumer.Siste14aVedtak(
            aktorId = navBruker.person.personident,
            innsatsgruppe = InnsatsgruppeV1.SPESIELT_TILPASSET_INNSATS,
        )

        every { pdlClient.hentIdenter(siste14aVedtak.aktorId) } returns
            listOf(Personident(navBruker.person.personident, false, IdentType.FOLKEREGISTERIDENT))

        innsatsgruppeConsumer.ingest(objectMapper.writeValueAsString(siste14aVedtak))

        val faktiskBruker = navBrukerRepository.get(navBruker.id)
        faktiskBruker.innsatsgruppe shouldBe InnsatsgruppeV1.SPESIELT_TILPASSET_INNSATS
    }

    @Test
    fun `ingest - bruker finnes ikke - oppdaterer ikke`() {
        val siste14aVedtak = InnsatsgruppeConsumer.Siste14aVedtak(
            aktorId = "1234",
            innsatsgruppe = InnsatsgruppeV1.SPESIELT_TILPASSET_INNSATS,
        )
        every { pdlClient.hentIdenter(siste14aVedtak.aktorId) } returns
            listOf(Personident("ukjent ident", false, IdentType.FOLKEREGISTERIDENT))

        innsatsgruppeConsumer.ingest(objectMapper.writeValueAsString(siste14aVedtak))

        // No exception = success (bruker not found, skipped)
    }
}
