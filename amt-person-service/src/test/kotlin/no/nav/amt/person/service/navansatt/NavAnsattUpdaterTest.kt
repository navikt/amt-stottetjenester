package no.nav.amt.person.service.navansatt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.testing.utils.withLogCapture
import no.nav.amt.person.service.clients.nom.NomClient
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.TestData.navGrunerlokka
import no.nav.amt.person.service.data.TestData.orgTilknytning
import no.nav.amt.person.service.navenhet.NavEnhetService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NavAnsattUpdaterTest {
    private val navAnsattRepository: NavAnsattRepository = mockk(relaxUnitFun = true)
    private val navAnsattService: NavAnsattService = mockk(relaxUnitFun = true)
    private val nomClient: NomClient = mockk()
    private val navEnhetService: NavEnhetService = mockk()
    private val updater = NavAnsattUpdater(navAnsattRepository, navAnsattService, nomClient, navEnhetService)

    @BeforeEach
    fun setup() = clearAllMocks()

    @Test
    suspend fun `oppdaterAlle - navIdent mangler hos Nom - logger warning`() {
        val ansatt1 = TestData.lagNavAnsatt()
        val ansatt2 = TestData.lagNavAnsatt()

        mockBatches(batchSize = 100, listOf(ansatt1, ansatt2))
        every { nomClient.hentNavAnsatte(listOf(ansatt1.navIdent, ansatt2.navIdent)) } returns
            listOf(
                NomNavAnsatt(
                    navIdent = ansatt1.navIdent,
                    navn = ansatt1.navn,
                    telefonnummer = ansatt1.telefon,
                    epost = ansatt1.epost,
                    orgTilknytning = orgTilknytning,
                ),
            )
        every { navEnhetService.hentEllerOpprettNavEnhet(any()) } returns navGrunerlokka

        withLogCapture(NavAnsattUpdater::class.java.name) { loggingEvents ->
            updater.oppdaterAlle()

            val logMessages = loggingEvents.map { it.message }

            logMessages shouldContain "Fant ikke Nav-ansatt med id ${ansatt2.id} i NOM"
            logMessages shouldNotContain "Fant ikke Nav-ansatt med id ${ansatt1.id} i NOM"
        }
    }

    @Test
    fun `oppdaterAlle - ansatt er ikke endret - oppdaterer ikke`() {
        val ansatt = TestData.lagNavAnsatt()

        mockBatches(batchSize = 100, listOf(ansatt))
        every { nomClient.hentNavAnsatte(listOf(ansatt.navIdent)) } returns
            listOf(
                NomNavAnsatt(
                    navIdent = ansatt.navIdent,
                    navn = ansatt.navn,
                    telefonnummer = ansatt.telefon,
                    epost = ansatt.epost,
                    orgTilknytning = orgTilknytning,
                ),
            )
        every { navEnhetService.hentEllerOpprettNavEnhet(any()) } returns navGrunerlokka

        updater.oppdaterAlle()

        verify(exactly = 0) { navAnsattService.upsertMany(setOf(ansatt)) }
    }

    @Test
    fun `oppdaterAlle - ansatt er endret - oppdaterer ansatt`() {
        val ansatt = TestData.lagNavAnsatt()
        val oppdatertAnsatt = ansatt.copy(navn = "Foo Bar")

        mockBatches(batchSize = 100, listOf(ansatt))
        every { nomClient.hentNavAnsatte(listOf(ansatt.navIdent)) } returns
            listOf(
                NomNavAnsatt(
                    navIdent = oppdatertAnsatt.navIdent,
                    navn = oppdatertAnsatt.navn,
                    telefonnummer = oppdatertAnsatt.telefon,
                    epost = oppdatertAnsatt.epost,
                    orgTilknytning = orgTilknytning,
                ),
            )
        every { navEnhetService.hentEllerOpprettNavEnhet(any()) } returns navGrunerlokka

        updater.oppdaterAlle()

        verify(exactly = 1) {
            navAnsattService.upsertMany(any())
        }
    }

    @Test
    fun `oppdaterAlle - behandler alle sider med begrenset batchstorrelse`() {
        val ansatt1 = TestData.lagNavAnsatt(navIdent = "A001")
        val ansatt2 = TestData.lagNavAnsatt(navIdent = "A002")
        val oppdatertAnsatt1 = ansatt1.copy(navn = "Foo Bar 1")
        val oppdatertAnsatt2 = ansatt2.copy(navn = "Foo Bar 2")

        mockBatches(batchSize = 1, listOf(ansatt1), listOf(ansatt2))
        every { nomClient.hentNavAnsatte(listOf(ansatt1.navIdent)) } returns
            listOf(
                NomNavAnsatt(
                    navIdent = oppdatertAnsatt1.navIdent,
                    navn = oppdatertAnsatt1.navn,
                    telefonnummer = oppdatertAnsatt1.telefon,
                    epost = oppdatertAnsatt1.epost,
                    orgTilknytning = orgTilknytning,
                ),
            )
        every { nomClient.hentNavAnsatte(listOf(ansatt2.navIdent)) } returns
            listOf(
                NomNavAnsatt(
                    navIdent = oppdatertAnsatt2.navIdent,
                    navn = oppdatertAnsatt2.navn,
                    telefonnummer = oppdatertAnsatt2.telefon,
                    epost = oppdatertAnsatt2.epost,
                    orgTilknytning = orgTilknytning,
                ),
            )
        every { navEnhetService.hentEllerOpprettNavEnhet(any()) } returns navGrunerlokka

        updater.oppdaterAlle(batchSize = 1)

        verify(exactly = 1) { navAnsattRepository.getBatchAfter(null, 1) }
        verify(exactly = 1) { navAnsattRepository.getBatchAfter(ansatt1.navIdent, 1) }
        verify(exactly = 1) { navAnsattRepository.getBatchAfter(ansatt2.navIdent, 1) }
        verify(exactly = 2) { navAnsattService.upsertMany(any()) }
    }

    @Test
    fun `oppdaterAlle - batchstorrelse ma vaere positiv`() {
        shouldThrow<IllegalArgumentException> {
            updater.oppdaterAlle(batchSize = 0)
        }
    }

    private fun mockBatches(
        batchSize: Int,
        vararg batches: List<NavAnsattDbo>,
    ) {
        var afterNavIdent: String? = null
        batches.forEach { batch ->
            every { navAnsattRepository.getBatchAfter(afterNavIdent, batchSize) } returns batch
            batch.lastOrNull()?.let { afterNavIdent = it.navIdent }
        }
        every { navAnsattRepository.getBatchAfter(afterNavIdent, batchSize) } returns emptyList()
    }
}
