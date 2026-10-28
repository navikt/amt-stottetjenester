package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.clients.norg.toNorgNavEnhetDto
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.kafka.KafkaMessageCreator
import no.nav.amt.person.service.data.kafka.message.KontorPayload
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.SisteOppfolgingsperiodeConsumer
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import org.junit.jupiter.api.Test

class SisteOppfolgingsperiodeConsumerTest(
    private val sisteOppfolgingsperiodeConsumer: SisteOppfolgingsperiodeConsumer,
    private val navBrukerRepository: NavBrukerRepository,
) : IntegrationTestBase() {
    @Test
    fun `ingest - bruker finnes, har ikke Nav-kontor - oppretter og oppdaterer Nav-kontor`() {
        val navEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navEnhet = null)

        val kafkaPayload = KafkaMessageCreator.lagSisteOppfolgingsperiodeMsg(
            ident = navBruker.person.personident,
            kontor = KontorPayload(kontorId = navEnhet.enhetId, kontorNavn = navEnhet.navn),
        )

        testDataRepository.insertNavBruker(navBruker)

        every { norgClient.hentNavEnhet(navEnhet.enhetId) } returns navEnhet.toNorgNavEnhetDto()

        sisteOppfolgingsperiodeConsumer.ingest(objectMapper.writeValueAsString(kafkaPayload))

        val faktiskBruker = navBrukerRepository.get(navBruker.id)

        assertSoftly(faktiskBruker.navEnhet.shouldNotBeNull()) {
            enhetId shouldBe navEnhet.enhetId
            navn shouldBe navEnhet.navn
        }
    }
}
