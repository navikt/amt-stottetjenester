package no.nav.amt.person.service.navansatt

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.person.service.clients.nom.NomClient
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.clients.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.TestData.navGrunerlokka
import no.nav.amt.person.service.data.TestData.orgTilknytning
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.utils.mockExecute
import no.nav.amt.person.service.utils.mockExecuteWithoutResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class NavAnsattServiceTest {
    private val navAnsattRepository: NavAnsattRepository = mockk(relaxUnitFun = true)
    private val nomClient: NomClient = mockk()
    private val veilarboppfolgingClient: VeilarboppfolgingClient = mockk()
    private val kafkaProducerService: KafkaProducerService = mockk(relaxUnitFun = true)
    private val navEnhetService: NavEnhetService = mockk()
    private val transactionTemplate: TransactionTemplate = mockk()
    private val service = NavAnsattService(
        navAnsattRepository = navAnsattRepository,
        nomClient = nomClient,
        veilarboppfolgingClient = veilarboppfolgingClient,
        kafkaProducerService = kafkaProducerService,
        navEnhetService = navEnhetService,
        transactionTemplate = transactionTemplate,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockExecute<NavAnsattDbo>(
            transactionTemplate = transactionTemplate,
        )
        mockExecuteWithoutResult(
            transactionTemplate = transactionTemplate,
        )
    }

    @Test
    fun `hentHellerOpprettAnsatt - ansatt finnes ikke - oppretter og returnerer ansatt`() {
        // Arrange
        val ansatt = TestData.lagNavAnsatt()

        every { navAnsattRepository.get(ansatt.navIdent) } returns null
        every { nomClient.hentNavAnsatt(ansatt.navIdent) } returns
            NomNavAnsatt(
                navIdent = ansatt.navIdent,
                navn = ansatt.navn,
                telefonnummer = ansatt.telefon,
                epost = ansatt.epost,
                orgTilknytning = orgTilknytning,
            )
        every { navAnsattRepository.upsert(any()) } returns ansatt
        every { navEnhetService.hentEllerOpprettNavEnhet(any()) } returns navGrunerlokka

        // Act
        val faktiskAnsatt = service.hentEllerOpprettAnsatt(ansatt.navIdent)

        // Assert
        assertSoftly(faktiskAnsatt) {
            navIdent shouldBe ansatt.navIdent
            navn shouldBe ansatt.navn
            telefon shouldBe ansatt.telefon
            epost shouldBe ansatt.epost
        }
    }
}
