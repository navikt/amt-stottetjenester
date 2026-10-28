package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.clients.norg.toNorgNavEnhetDto
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.kafka.KafkaMessageCreator
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.kafka.consumer.TildeltVeilederConsumer
import no.nav.amt.person.service.navansatt.NavAnsattRepository
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Personident
import org.junit.jupiter.api.Test

class TildeltVeilederConsumerTest(
    private val tildeltVeilederConsumer: TildeltVeilederConsumer,
    private val navBrukerRepository: NavBrukerRepository,
    private val navAnsattRepository: NavAnsattRepository,
) : IntegrationTestBase() {
    @Test
    fun `ingest - bruker finnes, ny veileder - oppretter og oppdaterer nav veileder`() {
        val navBruker = TestData.lagNavBruker()
        testDataRepository.insertNavBruker(navBruker)

        val payload = KafkaMessageCreator.lagTildeltVeilederMsg()
        val navAnsatt = TestData.lagNavAnsatt(navIdent = payload.veilederId)

        every { pdlClient.hentIdenter(payload.aktorId) } returns
            listOf(Personident(navBruker.person.personident, false, IdentType.FOLKEREGISTERIDENT))

        every { nomClient.hentNavAnsatt(navAnsatt.navIdent) } returns NomNavAnsatt(
            navIdent = navAnsatt.navIdent,
            navn = navAnsatt.navn,
            telefonnummer = navAnsatt.telefon,
            epost = navAnsatt.epost,
            orgTilknytning = TestData.orgTilknytning,
        )

        every { norgClient.hentNavEnhet(TestData.navGrunerlokka.enhetId) } returns
            TestData.navGrunerlokka.toNorgNavEnhetDto()

        tildeltVeilederConsumer.ingest(objectMapper.writeValueAsString(payload))

        val faktiskNavAnsatt = navAnsattRepository.get(navAnsatt.navIdent)

        assertSoftly(faktiskNavAnsatt.shouldNotBeNull()) {
            navIdent shouldBe navAnsatt.navIdent
            navn shouldBe navAnsatt.navn
            epost shouldBe navAnsatt.epost
            telefon shouldBe navAnsatt.telefon
        }

        val faktiskBruker = navBrukerRepository.get(navBruker.id)

        faktiskBruker.navVeileder.shouldNotBeNull()
        faktiskBruker.navVeileder.navIdent shouldBe navAnsatt.navIdent
    }

    @Test
    fun `ingest - bruker finnes ikke - oppdaterer ikke veileder`() {
        val payload = KafkaMessageCreator.lagTildeltVeilederMsg()
        every { pdlClient.hentIdenter(payload.aktorId) } returns
            listOf(Personident("ukjent ident", false, IdentType.FOLKEREGISTERIDENT))

        tildeltVeilederConsumer.ingest(objectMapper.writeValueAsString(payload))

        navAnsattRepository.get(payload.veilederId) shouldBe null
    }
}
