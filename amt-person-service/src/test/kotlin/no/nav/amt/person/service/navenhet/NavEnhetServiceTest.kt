package no.nav.amt.person.service.navenhet

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.person.service.clients.norg.NorgClient
import no.nav.amt.person.service.clients.norg.toNorgNavEnhetDto
import no.nav.amt.person.service.clients.oppfolgingskontor.Arbeidsoppfolging
import no.nav.amt.person.service.clients.oppfolgingskontor.OppfolgingskontorClient
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.utils.mockExecuteWithoutResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class NavEnhetServiceTest {
    private val norgClient: NorgClient = mockk()
    private val navEnhetRepository: NavEnhetRepository = mockk(relaxUnitFun = true)
    private val oppfolgingskontorClient: OppfolgingskontorClient = mockk()
    private val kafkaProducerService = mockk<KafkaProducerService>(relaxUnitFun = true)
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val service = NavEnhetService(
        navEnhetRepository = navEnhetRepository,
        norgClient = norgClient,
        oppfolgingskontorClient = oppfolgingskontorClient,
        kafkaProducerService = kafkaProducerService,
        transactionTemplate = transactionTemplate,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockExecuteWithoutResult(
            transactionTemplate = transactionTemplate,
        )
    }

    @Nested
    inner class HentNavEnhetForBruker {
        @Test
        fun `enhet finnes ikke - skal opprette enhet`() {
            // Arrange
            val navEnhet = TestData.lagNavEnhet()
            val personident = "FNR"
            every {
                oppfolgingskontorClient.hentKontorForBruker(personident)
            } returns Arbeidsoppfolging(navEnhet.enhetId, navEnhet.navn)
            every { navEnhetRepository.get(navEnhet.enhetId) } returns null
            every { norgClient.hentNavEnhet(navEnhet.enhetId) } returns navEnhet.toNorgNavEnhetDto()

            // Act
            val faktiskEnhet = service.hentNavEnhetForBruker(personident)

            // Assert
            assertSoftly(faktiskEnhet.shouldNotBeNull()) {
                enhetId shouldBe navEnhet.enhetId
                navn shouldBe navEnhet.navn
            }
            verify { kafkaProducerService.publiserNavEnhet(faktiskEnhet) }
        }

        @Test
        fun `bruker har ingen arbeidsoppfolgingsenhet - skal returnere null`() {
            // Arrange
            val personident = "FNR"
            every { oppfolgingskontorClient.hentKontorForBruker(personident) } returns null

            // Act
            val faktiskEnhet = service.hentNavEnhetForBruker(personident)

            // Assert
            faktiskEnhet shouldBe null
        }
    }

    @Test
    fun `oppdaterNavEnheter - enhet med nytt navn - oppdaterer enhet`() {
        // Arrange
        val enhet1 = TestData.lagNavEnhet(navn = "NAV Test 1")
        val enhet2 = TestData.lagNavEnhet(navn = "NAV Test 2")

        val oppdatertEnhet1 = enhet1.copy(navn = "Nytt Navn").toNorgNavEnhetDto()

        every { norgClient.hentNavEnheter(listOf(enhet1.enhetId, enhet2.enhetId)) } returns
            listOf(
                oppdatertEnhet1,
                enhet2.toNorgNavEnhetDto(),
            )

        // Act
        service.oppdaterNavEnheter(listOf(enhet1, enhet2))

        // Assert
        val enhet1MedNyttNavn = enhet1.copy(navn = "Nytt Navn")
        verify(exactly = 1) {
            navEnhetRepository.update(enhet1MedNyttNavn)
            kafkaProducerService.publiserNavEnhet(enhet1MedNyttNavn)
        }
        verify(exactly = 0) { navEnhetRepository.update(enhet2) }
    }
}
