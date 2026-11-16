package no.nav.amt.person.service.internal

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navansatt.NavAnsattRepository
import no.nav.amt.person.service.navansatt.NavAnsattUpdater
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navenhet.NavEnhetUpdateJob
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.PersonidentRepository
import no.nav.amt.person.service.utils.mockExecuteWithoutResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

class InternalServiceTest {
    private val personService: PersonService = mockk(relaxUnitFun = true)
    private val personRepository: PersonRepository = mockk()
    private val navBrukerRepository: NavBrukerRepository = mockk()
    private val navBrukerService: NavBrukerService = mockk(relaxUnitFun = true)
    private val personUpdater: PersonUpdater = mockk(relaxUnitFun = true)
    private val navAnsattRepository: NavAnsattRepository = mockk()
    private val kafkaProducerService: KafkaProducerService = mockk(relaxUnitFun = true)
    private val navAnsattUpdater: NavAnsattUpdater = mockk(relaxUnitFun = true)
    private val navEnhetUpdateJob: NavEnhetUpdateJob = mockk(relaxUnitFun = true)
    private val personidentRepository: PersonidentRepository = mockk()
    private val transactionTemplate: TransactionTemplate = mockk()

    private val service = InternalService(
        personService = personService,
        personRepository = personRepository,
        navBrukerRepository = navBrukerRepository,
        navBrukerService = navBrukerService,
        personUpdater = personUpdater,
        navAnsattRepository = navAnsattRepository,
        kafkaProducerService = kafkaProducerService,
        navAnsattUpdater = navAnsattUpdater,
        navEnhetUpdateJob = navEnhetUpdateJob,
        personidentRepository = personidentRepository,
        transactionTemplate = transactionTemplate,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockExecuteWithoutResult(transactionTemplate)
    }

    @Nested
    inner class OppdaterPersonidenter {
        @Test
        fun `delegerer til personUpdater med gitt offset`() {
            service.oppdaterPersonidenter(42)

            verify { personUpdater.oppdaterPersonidenter(42) }
        }
    }

    @Nested
    inner class OppdaterNavn {
        @Test
        fun `henter person og kaller oppdaterNavn`() {
            val person = TestData.lagPerson()
            every { personRepository.get(person.id) } returns person

            service.oppdaterNavn(person.id)

            verify { personService.oppdaterNavn(person) }
        }

        @Test
        fun `kaller oppdaterNavn selv om person har falsk identitet`() {
            val person = TestData.lagPerson(erFalskIdentitet = true)
            every { personRepository.get(person.id) } returns person

            service.oppdaterNavn(person.id)

            verify { personService.oppdaterNavn(person) }
        }
    }

    @Nested
    inner class RepubliserNavBrukere {
        @Test
        fun `publiserer alle brukere i batches`() {
            val bruker1 = TestData.lagNavBruker()
            val bruker2 = TestData.lagNavBruker()
            every { navBrukerRepository.getAllNavBrukere(0, 2) } returns listOf(bruker1, bruker2)
            every { navBrukerRepository.getAllNavBrukere(2, 2) } returns emptyList()

            service.republiserNavBrukere(startFromOffset = 0, batchSize = 2)

            verify { kafkaProducerService.publiserNavBruker(bruker1) }
            verify { kafkaProducerService.publiserNavBruker(bruker2) }
        }

        @Test
        fun `bruker startFromOffset`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.getAllNavBrukere(10, 500) } returns listOf(bruker)
            every { navBrukerRepository.getAllNavBrukere(510, 500) } returns emptyList()

            service.republiserNavBrukere(startFromOffset = 10, batchSize = 500)

            verify { kafkaProducerService.publiserNavBruker(bruker) }
        }
    }

    @Nested
    inner class OppdaterAdresseOgRepubliserNavBruker {
        @Test
        fun `henter bruker og kaller oppdaterAdresse`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.get(bruker.id) } returns bruker

            service.oppdaterAdresseOgRepubliserNavBruker(bruker.id)

            verify { navBrukerService.oppdaterAdresse(setOf(bruker.person.personident)) }
        }
    }

    @Nested
    inner class OppdaterInnsatsOgRepubliserNavBruker {
        @Test
        fun `henter bruker og kaller oppdaterOppfolgingsperiodeOgInnsatsgruppe`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.get(bruker.id) } returns bruker

            service.oppdaterInnsatsOgRepubliserNavBruker(bruker.id)

            verify { navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(bruker) }
        }
    }

    @Nested
    inner class OppdaterInnsatsOgRepubliserNavBrukere {
        @Test
        fun `bruker batchHandterNavBrukere når modifiedBefore er null`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.getAllNavBrukere(0, 500) } returns listOf(bruker)
            every { navBrukerRepository.getAllNavBrukere(500, 500) } returns emptyList()

            service.oppdaterInnsatsOgRepubliserNavBrukere(
                startFromOffset = 0,
                batchSize = 500,
                modifiedBefore = null,
                lastId = null,
            )

            verify { navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(bruker) }
        }

        @Test
        fun `bruker batchHandterNavBrukereByModifiedBefore når modifiedBefore er satt`() {
            val bruker = TestData.lagNavBruker()
            val modifiedBefore = LocalDate.now().minusDays(7)
            every { navBrukerRepository.getAllNavBrukere(500, modifiedBefore, null) } returns listOf(bruker)
            every { navBrukerRepository.getAllNavBrukere(500, modifiedBefore, bruker.id) } returns emptyList()

            service.oppdaterInnsatsOgRepubliserNavBrukere(
                startFromOffset = 0,
                batchSize = 500,
                modifiedBefore = modifiedBefore,
                lastId = null,
            )

            verify { navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(bruker) }
        }
    }

    @Nested
    inner class PubliserNavBruker {
        @Test
        fun `henter bruker og publiserer`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.get(bruker.id) } returns bruker

            service.publiserNavBruker(bruker.id)

            verify { kafkaProducerService.publiserNavBruker(bruker) }
        }
    }

    @Nested
    inner class RepubliserArrangorAnsatte {
        @Test
        fun `publiserer alle arrangøransatte i batches`() {
            val person1 = TestData.lagPerson()
            val person2 = TestData.lagPerson()
            every { personRepository.getAllWithRolle(0, 2, any()) } returns listOf(person1, person2)
            every { personRepository.getAllWithRolle(2, 2, any()) } returns emptyList()

            service.republiserArrangorAnsatte(startFromOffset = 0, batchSize = 2)

            verify { kafkaProducerService.publiserArrangorAnsatt(person1) }
            verify { kafkaProducerService.publiserArrangorAnsatt(person2) }
        }
    }

    @Nested
    inner class RepubliserNavAnsatte {
        @Test
        fun `henter alle ansatte og publiserer`() {
            val ansatt1 = TestData.lagNavAnsatt()
            val ansatt2 = TestData.lagNavAnsatt()
            every { navAnsattRepository.getAll() } returns listOf(ansatt1, ansatt2)

            service.republiserNavAnsatte()

            verify { kafkaProducerService.publiserNavAnsatt(ansatt1) }
            verify { kafkaProducerService.publiserNavAnsatt(ansatt2) }
        }
    }

    @Nested
    inner class OppdaterNavAnsatte {
        @Test
        fun `delegerer til navAnsattUpdater`() {
            service.oppdaterNavAnsatte()

            verify { navAnsattUpdater.oppdaterAlle() }
        }
    }

    @Nested
    inner class OppdaterNavEnheter {
        @Test
        fun `delegerer til navEnhetUpdateJob`() {
            service.oppdaterNavEnheter()

            verify { navEnhetUpdateJob.update() }
        }
    }

    @Nested
    inner class SynkroniserKrr {
        @Test
        fun `henter personidenter og synkroniserer`() {
            val personidenter = setOf("12345678901", "98765432100")
            every {
                navBrukerRepository.getPersonidenter(
                    offset = 0,
                    limit = 100,
                    notSyncedSince = any(),
                )
            } returns personidenter.toList()

            service.synkroniserKrr(offset = 0, batchSize = 100)

            verify { navBrukerService.syncKontaktinfoBulk(personidenter) }
        }
    }

    @Nested
    inner class SynkroniserKrrForPerson {
        @Test
        fun `finner bruker og oppdaterer kontaktinformasjon`() {
            val bruker = TestData.lagNavBruker()
            every { navBrukerRepository.get(bruker.person.personident) } returns bruker

            service.synkroniserKrrForPerson(bruker.person.personident)

            verify { navBrukerService.oppdaterKontaktinformasjon(bruker) }
        }

        @Test
        fun `kaster IllegalArgumentException når bruker ikke finnes`() {
            every { navBrukerRepository.get(any<String>()) } returns null

            shouldThrow<IllegalArgumentException> {
                service.synkroniserKrrForPerson("ukjent-ident")
            }
        }
    }

    @Nested
    inner class OppdaterManglendeKontaktinfo {
        @Test
        fun `synkroniserer alle personidenter med manglende kontaktinfo`() {
            val personidenter = listOf("12345678901", "98765432100")
            every { navBrukerRepository.getPersonidenterMedManglendeKontaktinfo(null, 200) } returns personidenter
            every { navBrukerRepository.getPersonidenterMedManglendeKontaktinfo(personidenter.last(), 200) } returns emptyList()

            service.oppdaterManglendeKontaktinfo(batchSize = 200)

            verify { navBrukerService.syncKontaktinfoBulk(personidenter.toSet()) }
        }
    }

    @Nested
    inner class RepubliserNavBrukereMedNyIdent {
        @Test
        fun `republiserer brukere med ny ident`() {
            val personId = UUID.randomUUID()
            val bruker = TestData.lagNavBruker()
            every { personidentRepository.getPersonIderMedFlerePersonidenter() } returns listOf(personId)
            every { navBrukerRepository.getByPersonId(personId) } returns bruker

            service.republiserNavBrukereMedNyIdent()

            verify { kafkaProducerService.publiserNavBruker(bruker) }
        }

        @Test
        fun `hopper over person uten Nav-bruker`() {
            val personId = UUID.randomUUID()
            every { personidentRepository.getPersonIderMedFlerePersonidenter() } returns listOf(personId)
            every { navBrukerRepository.getByPersonId(personId) } returns null

            service.republiserNavBrukereMedNyIdent()

            verify(exactly = 0) { kafkaProducerService.publiserNavBruker(any()) }
        }
    }
}
