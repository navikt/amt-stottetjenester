package no.nav.amt.person.service.integration.kafka.ingestor

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.kafka.KafkaMessageCreator
import no.nav.amt.person.service.kafka.consumer.SisteOppfolgingsperiodeConsumer
import no.nav.amt.person.service.kafka.consumer.dto.KontorPayload
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.utils.toSystemZoneLocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.ObjectMapper
import java.time.ZonedDateTime

@JsonTest
class SisteOppfolgingsperiodeConsumerUnitTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val navBrukerRepository: NavBrukerRepository = mockk(relaxUnitFun = true)
    private val navBrukerService: NavBrukerService = mockk(relaxUnitFun = true)
    private val navEnhetService: NavEnhetService = mockk()
    private val sisteOppfolgingsperiodeConsumer = SisteOppfolgingsperiodeConsumer(
        navBrukerRepository = navBrukerRepository,
        navBrukerService = navBrukerService,
        navEnhetService = navEnhetService,
        objectMapper = objectMapper,
    )

    @BeforeEach
    fun setup() = clearAllMocks()

    @Test
    fun `ingest - bruker finnes ikke - gjor ingenting`() {
        val msg = KafkaMessageCreator.lagSisteOppfolgingsperiodeMsg()

        every { navBrukerRepository.get(msg.ident) } returns null

        sisteOppfolgingsperiodeConsumer.ingest(objectMapper.writeValueAsString(msg))

        verify(exactly = 0) {
            navEnhetService.hentEllerOpprettNavEnhet(any())
            navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(any(), any())
        }
    }

    @Test
    fun `ingest - oppfolging avsluttet, kontor er null - beholder nav enhet og oppdaterer oppfolgingsperiode`() {
        val navEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navEnhet = navEnhet)
        val sluttTidspunkt = ZonedDateTime.now()
        val msg = KafkaMessageCreator.lagSisteOppfolgingsperiodeMsg(
            ident = navBruker.person.personident,
            kontor = null,
            sisteEndringsType = "OPPFOLGING_AVSLUTTET",
            sluttTidspunkt = sluttTidspunkt,
        )

        every { navBrukerRepository.get(msg.ident) } returns navBruker

        sisteOppfolgingsperiodeConsumer.ingest(objectMapper.writeValueAsString(msg))

        verify(exactly = 0) { navEnhetService.hentEllerOpprettNavEnhet(any()) }
        verify(exactly = 1) {
            navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(
                navBruker = navBruker,
                oppfolgingsperiode = Oppfolgingsperiode(
                    id = msg.oppfolgingsperiodeUuid,
                    startdato = msg.startTidspunkt.toSystemZoneLocalDateTime(),
                    sluttdato = sluttTidspunkt.toSystemZoneLocalDateTime(),
                ),
            )
        }
    }

    @Test
    fun `ingest - kontor er endret - oppdaterer nav enhet og oppfolgingsperiode`() {
        val gammeltNavEnhet = TestData.lagNavEnhet()
        val nyttNavEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navEnhet = gammeltNavEnhet)
        val msg = KafkaMessageCreator.lagSisteOppfolgingsperiodeMsg(
            ident = navBruker.person.personident,
            kontor = KontorPayload(kontorId = nyttNavEnhet.enhetId, kontorNavn = nyttNavEnhet.navn),
        )

        every { navBrukerRepository.get(msg.ident) } returns navBruker
        every { navEnhetService.hentEllerOpprettNavEnhet(nyttNavEnhet.enhetId) } returns nyttNavEnhet

        sisteOppfolgingsperiodeConsumer.ingest(objectMapper.writeValueAsString(msg))

        val brukerSlot = slot<NavBrukerDbo>()
        val periodeSlot = slot<Oppfolgingsperiode>()
        verify(exactly = 1) {
            navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(capture(brukerSlot), capture(periodeSlot))
        }
        brukerSlot.captured shouldBe navBruker.copy(navEnhet = nyttNavEnhet)
        periodeSlot.captured.id shouldBe msg.oppfolgingsperiodeUuid
        periodeSlot.captured.sluttdato shouldBe null
    }
}
