package no.nav.amt.person.service.navbruker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.person.service.clients.krr.Kontaktinformasjon
import no.nav.amt.person.service.clients.krr.KrrProxyClient
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.clients.pdl.PdlPerson
import no.nav.amt.person.service.clients.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.person.service.clients.veilarbvedtaksstotte.VeilarbvedtaksstotteClient
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.RolleRepository
import no.nav.amt.person.service.person.dbo.PersonDbo.Companion.UNKNOWN_NAME
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.mockExecuteWithoutResult
import no.nav.poao_tilgang.client.PoaoTilgangClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

class NavBrukerServiceTest {
    private val navBrukerRepository: NavBrukerRepository = mockk(relaxUnitFun = true)
    private val rolleRepository: RolleRepository = mockk(relaxUnitFun = true)
    private val personService: PersonService = mockk(relaxUnitFun = true)
    private val navAnsattService: NavAnsattService = mockk()
    private val navEnhetService: NavEnhetService = mockk()
    private val krrProxyClient: KrrProxyClient = mockk()
    private val poaoTilgangClient: PoaoTilgangClient = mockk()
    private val pdlClient: PdlClient = mockk()
    private val veilarboppfolgingClient: VeilarboppfolgingClient = mockk()
    private val veilarbvedtaksstotteClient: VeilarbvedtaksstotteClient = mockk()
    private val kafkaProducerService: KafkaProducerService = mockk(relaxUnitFun = true)
    private val transactionTemplate: TransactionTemplate = mockk()

    private val sut = NavBrukerService(
        navBrukerRepository = navBrukerRepository,
        rolleRepository = rolleRepository,
        personService = personService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        krrProxyClient = krrProxyClient,
        poaoTilgangClient = poaoTilgangClient,
        pdlClient = pdlClient,
        veilarboppfolgingClient = veilarboppfolgingClient,
        veilarbvedtaksstotteClient = veilarbvedtaksstotteClient,
        kafkaProducerService = kafkaProducerService,
        transactionTemplate = transactionTemplate,
    )

    @BeforeEach
    fun setup() = clearAllMocks()

    @Nested
    inner class HentEllerOpprettNavBrukerTests {
        @Test
        fun `hentEllerOpprettNavBruker - bruker finnes ikke - oppretter og returnerer ny bruker`() {
            val navBruker = TestData.lagNavBruker()
            val pdlPerson = TestData.lagPdlPerson(person = navBruker.person)

            mocksForHentEllerOpprettNavBruker(navBruker, pdlPerson)
            every { navBrukerRepository.get(any<UUID>()) } returns navBruker

            val faktiskBruker = sut.hentEllerOpprettNavBruker(navBruker.person.personident)

            assertSoftly(faktiskBruker) {
                person.id shouldBe navBruker.person.id
                person.id shouldBe navBruker.person.id
                navVeileder?.id shouldBe navBruker.navVeileder?.id
                navEnhet?.id shouldBe navBruker.navEnhet?.id
                telefon shouldBe navBruker.telefon
                epost shouldBe navBruker.epost
                erSkjermet shouldBe navBruker.erSkjermet
                adressebeskyttelse shouldBe null
                oppfolgingsperioder shouldBe navBruker.oppfolgingsperioder
                innsatsgruppe shouldBe navBruker.innsatsgruppe
            }
            verify(exactly = 1) {
                personService.hentEllerOpprettPerson(navBruker.person.personident, pdlPerson)
            }
        }

        @Test
        fun `hentEllerOpprettNavBruker - bruker er adressebeskyttet - oppretter bruker`() {
            val navBruker = TestData.lagNavBruker(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG)
            val pdlPerson = TestData.lagPdlPerson(person = navBruker.person)

            mocksForHentEllerOpprettNavBruker(navBruker, pdlPerson)
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker

            val faktiskBruker = sut.hentEllerOpprettNavBruker(navBruker.person.personident)
            faktiskBruker.adressebeskyttelse shouldBe Adressebeskyttelse.STRENGT_FORTROLIG
        }

        @Test
        fun `hentEllerOpprettNavBruker - ikke aktiv oppfolgingsperiode - innsatsgruppe er null`() {
            val navBruker = TestData.lagNavBruker(
                innsatsgruppe = null,
                oppfolgingsperioder =
                    listOf(
                        TestData.lagOppfolgingsperiode(
                            startdato = LocalDateTime.now().minusYears(1),
                            sluttdato = LocalDateTime.now().minusDays(29),
                        ),
                    ),
            )
            val pdlPerson = TestData.lagPdlPerson(person = navBruker.person)

            mocksForHentEllerOpprettNavBruker(navBruker, pdlPerson)
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker

            val faktiskBruker = sut.hentEllerOpprettNavBruker(navBruker.person.personident)
            faktiskBruker.oppfolgingsperioder shouldBe navBruker.oppfolgingsperioder
            faktiskBruker.innsatsgruppe shouldBe null
        }

        @Test
        fun `hentEllerOpprettNavBruker - eksisterende bruker med ukjent navn - oppdaterer person fra PDL`() {
            val personident = TestData.randomIdent()
            val gammelPerson = TestData.lagPerson(
                personident = personident,
                fornavn = UNKNOWN_NAME,
                etternavn = UNKNOWN_NAME,
                erFalskIdentitet = false,
                modifiedAt = LocalDateTime.now().minusDays(2),
            )
            val oppdatertPerson = gammelPerson.copy(
                fornavn = "Nytt Fornavn",
                mellomnavn = "Nytt Mellomnavn",
                etternavn = "Nytt Etternavn",
                erFalskIdentitet = true,
            )
            val gammelNavBruker = TestData.lagNavBruker(person = gammelPerson)
            val oppdatertNavBruker = gammelNavBruker.copy(person = oppdatertPerson)

            every { navBrukerRepository.get(personident) } returns gammelNavBruker
            every { navBrukerRepository.getByPersonId(oppdatertPerson.id) } returns oppdatertNavBruker
            every { personService.hentEllerOpprettPerson(personident, forceFetchFromPdl = true) } returns oppdatertPerson

            val faktiskBruker = sut.hentEllerOpprettNavBruker(personident)

            faktiskBruker.person shouldBe oppdatertPerson
            verify(exactly = 1) { personService.hentEllerOpprettPerson(personident, forceFetchFromPdl = true) }
            verify(exactly = 1) { navBrukerRepository.get(personident) }
            verify(exactly = 1) { navBrukerRepository.getByPersonId(oppdatertPerson.id) }
        }

        @Test
        fun `hentEllerOpprettNavBruker - nylig oppdatert ukjent navn - refresher ikke fra PDL`() {
            val personident = TestData.randomIdent()
            val person = TestData.lagPerson(
                personident = personident,
                fornavn = UNKNOWN_NAME,
                etternavn = UNKNOWN_NAME,
                erFalskIdentitet = false,
                modifiedAt = LocalDateTime.now().minusHours(1),
            )
            val navBruker = TestData.lagNavBruker(person = person)

            every { navBrukerRepository.get(personident) } returns navBruker

            val faktiskBruker = sut.hentEllerOpprettNavBruker(personident)

            faktiskBruker shouldBe navBruker
            verify(exactly = 0) { personService.hentEllerOpprettPerson(personident, forceFetchFromPdl = true) }
            verify(exactly = 0) { navBrukerRepository.getByPersonId(any()) }
        }

        @Test
        fun `hentEllerOpprettNavBruker - falsk identitet med ukjent navn og gammel modifiedAt - refresher ikke fra PDL`() {
            val personident = TestData.randomIdent()
            val person = TestData.lagPerson(
                personident = personident,
                fornavn = UNKNOWN_NAME,
                etternavn = UNKNOWN_NAME,
                erFalskIdentitet = true,
                modifiedAt = LocalDateTime.now().minusDays(2),
            )
            val navBruker = TestData.lagNavBruker(person = person)

            every { navBrukerRepository.get(personident) } returns navBruker

            val faktiskBruker = sut.hentEllerOpprettNavBruker(personident)

            faktiskBruker shouldBe navBruker
            verify(exactly = 0) { personService.hentEllerOpprettPerson(personident, forceFetchFromPdl = true) }
            verify(exactly = 0) { navBrukerRepository.getByPersonId(any()) }
        }
    }

    @Nested
    inner class SyncKontaktinfoBulkTests {
        @Test
        fun `syncKontaktinfoBulk - telefon er registrert i krr - oppdaterer bruker med telefon fra krr`() {
            val navBruker = TestData.lagNavBruker()
            val kontaktinfo = Kontaktinformasjon(
                "ny epost",
                "krr-telefon",
            )
            val kontakinfoForPersoner = mapOf(navBruker.person.personident to kontaktinfo)

            every { navBrukerRepository.finnBrukerId(navBruker.person.personident) } returns navBruker.id
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker
            every {
                krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident))
            } returns Result.success(kontakinfoForPersoner)

            mockExecuteWithoutResult(transactionTemplate)

            sut.syncKontaktinfoBulk(setOf(navBruker.person.personident))

            val expectedNavBruker = navBruker.copy(
                telefon = kontaktinfo.telefonnummer,
                epost = kontaktinfo.epost,
                sisteKrrSync = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS),
            )

            verify(exactly = 1) {
                krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident))

                navBrukerRepository.upsert(
                    match {
                        expectedNavBruker.id == it.id &&
                            expectedNavBruker.person == it.person &&
                            expectedNavBruker.navEnhet == it.navEnhet &&
                            expectedNavBruker.navVeileder == it.navVeileder &&
                            expectedNavBruker.telefon == it.telefon &&
                            expectedNavBruker.epost == it.epost &&
                            expectedNavBruker.erSkjermet == it.erSkjermet &&
                            expectedNavBruker.adresse == it.adresse &&
                            expectedNavBruker.sisteKrrSync ==
                            it.sisteKrrSync.shouldNotBeNull().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    },
                )
            }
        }

        @Test
        fun `syncKontaktinfoBulk - telefon er ikke registrert i krr - oppdaterer bruker med telefon fra pdl`() {
            val navBruker = TestData.lagNavBruker()
            val krrKontaktinfo = Kontaktinformasjon(
                "ny epost",
                null,
            )
            val kontakinfoForPersoner = mapOf(navBruker.person.personident to krrKontaktinfo)

            val pdlTelefon = "pdl-telefon"

            every { navBrukerRepository.finnBrukerId(navBruker.person.personident) } returns navBruker.id
            every { pdlClient.hentTelefon(navBruker.person.personident) } returns pdlTelefon
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker
            every {
                krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident))
            } returns Result.success(kontakinfoForPersoner)

            mockExecuteWithoutResult(transactionTemplate)

            sut.syncKontaktinfoBulk(setOf(navBruker.person.personident))

            val expectedNavBruker = navBruker.copy(
                telefon = pdlTelefon,
                epost = krrKontaktinfo.epost,
                sisteKrrSync = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS),
            )

            verify(exactly = 1) {
                pdlClient.hentTelefon(navBruker.person.personident)
                krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident))

                navBrukerRepository.upsert(
                    match {
                        expectedNavBruker.id == it.id &&
                            expectedNavBruker.person == it.person &&
                            expectedNavBruker.navEnhet == it.navEnhet &&
                            expectedNavBruker.navVeileder == it.navVeileder &&
                            expectedNavBruker.telefon == it.telefon &&
                            expectedNavBruker.epost == it.epost &&
                            expectedNavBruker.erSkjermet == it.erSkjermet &&
                            expectedNavBruker.adresse == it.adresse &&
                            expectedNavBruker.sisteKrrSync ==
                            it.sisteKrrSync.shouldNotBeNull().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    },
                )
            }
        }

        @Test
        fun `syncKontaktinfoBulk - krr feiler - oppdaterer ikke`() {
            val navBruker = TestData.lagNavBruker()

            every { navBrukerRepository.finnBrukerId(navBruker.person.personident) } returns navBruker.id
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker
            every {
                krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident))
            } returns Result.failure(RuntimeException())

            sut.syncKontaktinfoBulk(setOf(navBruker.person.personident))

            verify(exactly = 1) { krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident)) }
            verify(exactly = 0) { navBrukerRepository.upsert(any()) }
        }
    }

    @Nested
    inner class OppdaterKontaktinformasjonTests {
        @Test
        fun `oppdaterKontaktinformasjon - bruker har ny kontaktinfo - oppdaterer bruker`() {
            val navBruker = TestData.lagNavBruker().copy(sisteKrrSync = LocalDateTime.now().minusWeeks(4))
            val kontaktinfo = Kontaktinformasjon(
                "ny epost",
                "krr-telefon",
            )

            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker
            every {
                krrProxyClient.hentKontaktinformasjon(navBruker.person.personident)
            } returns Result.success(kontaktinfo)

            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterKontaktinformasjon(navBruker)

            val expectedNavBruker = navBruker.copy(
                telefon = kontaktinfo.telefonnummer,
                epost = kontaktinfo.epost,
                sisteKrrSync = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS),
            )

            verify(exactly = 1) {
                krrProxyClient.hentKontaktinformasjon(navBruker.person.personident)

                navBrukerRepository.upsert(
                    match {
                        expectedNavBruker.id == it.id &&
                            expectedNavBruker.person == it.person &&
                            expectedNavBruker.navEnhet == it.navEnhet &&
                            expectedNavBruker.navVeileder == it.navVeileder &&
                            expectedNavBruker.telefon == it.telefon &&
                            expectedNavBruker.epost == it.epost &&
                            expectedNavBruker.erSkjermet == it.erSkjermet &&
                            expectedNavBruker.adresse == it.adresse &&
                            expectedNavBruker.sisteKrrSync ==
                            it.sisteKrrSync.shouldNotBeNull().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    },
                )
            }
        }

        @Test
        fun `oppdaterKontaktinformasjon - telefon er ikke registrert i krr - oppdaterer bruker med telefon fra pdl`() {
            val navBruker = TestData.lagNavBruker().copy(sisteKrrSync = LocalDateTime.now().minusWeeks(4))
            val krrKontaktinfo = Kontaktinformasjon(
                "ny epost",
                null,
            )

            val pdlTelefon = "pdl-telefon"

            every { pdlClient.hentTelefon(navBruker.person.personident) } returns pdlTelefon
            every { navBrukerRepository.get(navBruker.person.personident) } returns navBruker
            every { navBrukerRepository.get(navBruker.id) } returns navBruker
            every {
                krrProxyClient.hentKontaktinformasjon(navBruker.person.personident)
            } returns Result.success(krrKontaktinfo)

            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterKontaktinformasjon(navBruker)

            val expectedNavBruker = navBruker.copy(
                telefon = pdlTelefon,
                epost = krrKontaktinfo.epost,
                sisteKrrSync = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS),
            )

            verify(exactly = 1) {
                pdlClient.hentTelefon(navBruker.person.personident)
                krrProxyClient.hentKontaktinformasjon(navBruker.person.personident)

                navBrukerRepository.upsert(
                    match {
                        expectedNavBruker.id == it.id &&
                            expectedNavBruker.person == it.person &&
                            expectedNavBruker.navEnhet == it.navEnhet &&
                            expectedNavBruker.navVeileder == it.navVeileder &&
                            expectedNavBruker.telefon == it.telefon &&
                            expectedNavBruker.epost == it.epost &&
                            expectedNavBruker.erSkjermet == it.erSkjermet &&
                            expectedNavBruker.adresse == it.adresse &&
                            expectedNavBruker.sisteKrrSync ==
                            it.sisteKrrSync.shouldNotBeNull().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    },
                )
            }
        }

        @Test
        fun `oppdaterKontaktinformasjon - krr feiler - oppdaterer ikke`() {
            val bruker = TestData.lagNavBruker().copy(sisteKrrSync = LocalDateTime.now().minusWeeks(4))

            every { navBrukerRepository.get(bruker.person.personident) } returns bruker
            every {
                krrProxyClient.hentKontaktinformasjon(bruker.person.personident)
            } returns Result.failure(RuntimeException())

            sut.oppdaterKontaktinformasjon(bruker)

            verify(exactly = 1) { krrProxyClient.hentKontaktinformasjon(bruker.person.personident) }
            verify(exactly = 0) { navBrukerRepository.upsert(any()) }
        }
    }

    @Nested
    inner class OppdaterOppfolgingsperiodeOgInnsatsgruppeTests {
        val brukerInTest =
            TestData.lagNavBruker(
                oppfolgingsperioder =
                    listOf(
                        Oppfolgingsperiode(
                            id = UUID.randomUUID(),
                            startdato = LocalDateTime.now().minusYears(3),
                            sluttdato = LocalDateTime.now().minusYears(1),
                        ),
                    ),
            )

        @BeforeEach
        fun setup() {
            mockExecuteWithoutResult(transactionTemplate)

            every { navBrukerRepository.get(brukerInTest.id) } returns brukerInTest
            every {
                veilarbvedtaksstotteClient.hentInnsatsgruppe(brukerInTest.person.personident)
            } returns InnsatsgruppeV1.STANDARD_INNSATS
        }

        @Test
        fun `oppdaterOppfolgingsperiode - har ingen oppfolgingsperioder - lagrer`() {
            val bruker = brukerInTest.copy(oppfolgingsperioder = emptyList())
            val oppfolgingsperiode = TestData.lagOppfolgingsperiode()

            every { navBrukerRepository.get(bruker.id) } returns bruker

            sut.oppdaterOppfolgingsperiodeOgInnsatsgruppe(bruker.id, oppfolgingsperiode)

            verify(exactly = 1) {
                navBrukerRepository.upsert(
                    match {
                        it.oppfolgingsperioder == listOf(oppfolgingsperiode)
                    },
                )
                veilarbvedtaksstotteClient.hentInnsatsgruppe(bruker.person.personident)
            }
        }

        @Test
        fun `har eldre oppfolgingsperiode - lagrer`() {
            val oppfolgingsperiode = TestData.lagOppfolgingsperiode()

            sut.oppdaterOppfolgingsperiodeOgInnsatsgruppe(brukerInTest.id, oppfolgingsperiode)

            verify(exactly = 1) {
                navBrukerRepository.upsert(
                    match {
                        it.oppfolgingsperioder.size == 2 &&
                            it.oppfolgingsperioder.find { oppfolgingsPeriode ->
                                oppfolgingsPeriode.id ==
                                    brukerInTest.oppfolgingsperioder.first().id
                            } ==
                            brukerInTest.oppfolgingsperioder.first() &&
                            it.oppfolgingsperioder.find { oppfolgingsPeriode -> oppfolgingsPeriode.id == oppfolgingsperiode.id } ==
                            oppfolgingsperiode
                    },
                )
                veilarbvedtaksstotteClient.hentInnsatsgruppe(brukerInTest.person.personident)
            }
        }

        @Test
        fun `har samme oppfolgingsperiode, annen sluttdato - oppdaterer`() {
            val oppfolgingsperiode = brukerInTest.oppfolgingsperioder.first().copy(sluttdato = null)

            sut.oppdaterOppfolgingsperiodeOgInnsatsgruppe(brukerInTest.id, oppfolgingsperiode)

            verify(exactly = 1) {
                navBrukerRepository.upsert(
                    match {
                        it.oppfolgingsperioder.size == 1 &&
                            it.oppfolgingsperioder.find { oppfolgingsPeriode ->
                                oppfolgingsPeriode.id ==
                                    brukerInTest.oppfolgingsperioder.first().id
                            } ==
                            oppfolgingsperiode
                    },
                )
                veilarbvedtaksstotteClient.hentInnsatsgruppe(brukerInTest.person.personident)
            }
        }

        @Test
        fun `har samme oppfolgingsperiode, ingen endring - oppdaterer ikke`() {
            val oppfolgingsperiode = brukerInTest.oppfolgingsperioder.first()

            sut.oppdaterOppfolgingsperiodeOgInnsatsgruppe(brukerInTest.id, oppfolgingsperiode)

            verify(exactly = 0) {
                navBrukerRepository.upsert(any())
                veilarbvedtaksstotteClient.hentInnsatsgruppe(any())
            }
        }
    }

    @Nested
    inner class OppdaterInnsatsgruppeTests {
        @Test
        fun `oppdaterInnsatsgruppe - har aktiv oppfolgingsperiode - lagrer`() {
            val bruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    Oppfolgingsperiode(
                        id = UUID.randomUUID(),
                        startdato = LocalDateTime.now().minusYears(3),
                        sluttdato = null,
                    ),
                ),
                innsatsgruppe = InnsatsgruppeV1.STANDARD_INNSATS,
            )
            every { navBrukerRepository.get(bruker.id) } returns bruker
            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterInnsatsgruppe(bruker.id, InnsatsgruppeV1.SITUASJONSBESTEMT_INNSATS)

            verify(exactly = 1) {
                navBrukerRepository.upsert(
                    match {
                        it.innsatsgruppe == InnsatsgruppeV1.SITUASJONSBESTEMT_INNSATS
                    },
                )
            }
        }

        @Test
        fun `oppdaterInnsatsgruppe - har aktiv oppfolgingsperiode, ingen endring - lagrer ikke`() {
            val bruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    Oppfolgingsperiode(
                        id = UUID.randomUUID(),
                        startdato = LocalDateTime.now().minusYears(3),
                        sluttdato = null,
                    ),
                ),
                innsatsgruppe = InnsatsgruppeV1.STANDARD_INNSATS,
            )
            every { navBrukerRepository.get(bruker.id) } returns bruker
            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterInnsatsgruppe(bruker.id, InnsatsgruppeV1.STANDARD_INNSATS)

            verify(exactly = 0) { navBrukerRepository.upsert(any()) }
        }

        @Test
        fun `oppdaterInnsatsgruppe - har ikke aktiv oppfolgingsperiode - lagrer innsatsgruppe null`() {
            val bruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    Oppfolgingsperiode(
                        id = UUID.randomUUID(),
                        startdato = LocalDateTime.now().minusYears(3),
                        sluttdato = LocalDateTime.now().minusMonths(2),
                    ),
                ),
                innsatsgruppe = InnsatsgruppeV1.STANDARD_INNSATS,
            )
            every { navBrukerRepository.get(bruker.id) } returns bruker
            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterInnsatsgruppe(bruker.id, InnsatsgruppeV1.SITUASJONSBESTEMT_INNSATS)

            verify(exactly = 1) {
                navBrukerRepository.upsert(
                    match {
                        it.innsatsgruppe == null
                    },
                )
            }
        }

        @Test
        fun `oppdaterInnsatsgruppe - har ikke aktiv oppfolgingsperiode, ikke innsatsgruppe - oppdaterer ikke`() {
            val bruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    Oppfolgingsperiode(
                        id = UUID.randomUUID(),
                        startdato = LocalDateTime.now().minusYears(3),
                        sluttdato = LocalDateTime.now().minusMonths(2),
                    ),
                ),
                innsatsgruppe = null,
            )
            every { navBrukerRepository.get(bruker.id) } returns bruker
            mockExecuteWithoutResult(transactionTemplate)

            sut.oppdaterInnsatsgruppe(bruker.id, InnsatsgruppeV1.SITUASJONSBESTEMT_INNSATS)

            verify(exactly = 0) { navBrukerRepository.upsert(any()) }
        }
    }

    private fun mocksForHentEllerOpprettNavBruker(
        navBruker: NavBrukerDbo,
        pdlPerson: PdlPerson,
    ) {
        val person = navBruker.person
        val veileder = navBruker.navVeileder.shouldNotBeNull()
        val navEnhet = navBruker.navEnhet.shouldNotBeNull()
        val kontaktinformasjon = Kontaktinformasjon(navBruker.epost, navBruker.telefon)

        every { navBrukerRepository.get(person.personident) } returns null

        every { pdlClient.hentPerson(person.personident) } returns pdlPerson
        every { veilarboppfolgingClient.hentOppfolgingperioder(person.personident) } returns navBruker.oppfolgingsperioder
        every { veilarbvedtaksstotteClient.hentInnsatsgruppe(person.personident) } returns navBruker.innsatsgruppe
        every { personService.hentEllerOpprettPerson(person.personident, pdlPerson) } returns person
        every { navAnsattService.hentBrukersVeileder(person.personident) } returns veileder
        every { navEnhetService.hentNavEnhetForBruker(person.personident) } returns navEnhet
        every { krrProxyClient.hentKontaktinformasjon(person.personident) } returns Result.success(kontaktinformasjon)
        every { poaoTilgangClient.erSkjermetPerson(person.personident) } returns
            ApiResult(result = navBruker.erSkjermet, throwable = null)
        every { rolleRepository.harRolle(person.id, Rolle.NAV_BRUKER) } returns false
        every { navBrukerRepository.getByPersonId(person.id) } returns navBruker

        mockExecuteWithoutResult(transactionTemplate)
    }
}
