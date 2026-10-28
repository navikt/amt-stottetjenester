package no.nav.amt.person.service.internal

import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navansatt.NavAnsattRepository
import no.nav.amt.person.service.navansatt.NavAnsattUpdater
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navenhet.NavEnhetUpdateJob
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.PersonidentRepository
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.EnvUtils.isDev
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class InternalService(
    private val personService: PersonService,
    private val personRepository: PersonRepository,
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val personUpdater: PersonUpdater,
    private val navAnsattRepository: NavAnsattRepository,
    private val kafkaProducerService: KafkaProducerService,
    private val navAnsattUpdater: NavAnsattUpdater,
    private val navEnhetUpdateJob: NavEnhetUpdateJob,
    private val personidentRepository: PersonidentRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun opprettPerson(dollyIdent: String) {
        if (isDev()) personService.hentEllerOpprettPerson(dollyIdent)
    }

    fun opprettNavBruker(dollyIdent: String) {
        if (isDev()) navBrukerService.hentEllerOpprettNavBruker(dollyIdent)
    }

    fun oppdaterPersonidenter(offset: Int) {
        personUpdater.oppdaterPersonidenter(offset)
    }

    fun oppdaterNavn(id: UUID) {
        val person = personRepository.get(id)
        if (person.erFalskIdentitet) log.warn("Person $id har erFalskIdentitet = true")
        personService.oppdaterNavn(person)
    }

    fun republiserNavBrukere(
        startFromOffset: Int,
        batchSize: Int,
    ) {
        batchHandterNavBrukere(startFromOffset, batchSize) { navBruker ->
            republiser { kafkaProducerService.publiserNavBruker(navBruker) }
        }
    }

    fun oppdaterAdresseOgRepubliserNavBrukere(
        batchSize: Int,
        modifiedBefore: LocalDateTime,
        lastId: UUID?,
    ) {
        log.info("Oppdaterer adresse for alle Nav-brukere som mangler adresse")
        oppdaterAdresseHvisManglerOgRepubliser(modifiedBefore, batchSize, lastId)
    }

    fun oppdaterAdresseOgRepubliserNavBruker(id: UUID) {
        log.info("Oppdaterer adresse for Nav-bruker $id")
        val navBruker = navBrukerRepository.get(id)
        navBrukerService.oppdaterAdresse(setOf(navBruker.person.personident))
        log.info("Oppdaterte adresse for Nav-bruker $id")
    }

    fun oppdaterInnsatsOgRepubliserNavBrukere(
        startFromOffset: Int,
        batchSize: Int,
        modifiedBefore: LocalDate?,
        lastId: UUID?,
    ) {
        if (modifiedBefore != null) {
            batchHandterNavBrukereByModifiedBefore(modifiedBefore, batchSize, lastId) {
                navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(it)
            }
        } else {
            batchHandterNavBrukere(startFromOffset, batchSize) {
                navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(it)
            }
        }
    }

    fun oppdaterInnsatsOgRepubliserNavBruker(id: UUID) {
        log.info("Oppdaterer bruker $id")
        val navBruker = navBrukerRepository.get(id)
        navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(navBruker)
        log.info("Oppdaterte bruker $id")
    }

    fun publiserNavBruker(navBrukerId: UUID) {
        val bruker = navBrukerRepository.get(navBrukerId)
        republiser { kafkaProducerService.publiserNavBruker(bruker) }
    }

    fun republiserArrangorAnsatte(
        startFromOffset: Int,
        batchSize: Int,
    ) {
        var offset = startFromOffset
        var ansatte: List<PersonDbo>

        do {
            ansatte = personRepository.getAllWithRolle(offset, batchSize, Rolle.ARRANGOR_ANSATT)
            ansatte.forEach { republiser { kafkaProducerService.publiserArrangorAnsatt(it) } }
            log.info("Publiserte arrangøransatte fra offset $offset til ${offset + ansatte.size}")
            offset += batchSize
        } while (ansatte.isNotEmpty())
    }

    fun republiserNavAnsatte() {
        val ansatte = navAnsattRepository.getAll()
        ansatte.forEach { republiser { kafkaProducerService.publiserNavAnsatt(it) } }
        log.info("Publiserte ${ansatte.size} navansatte")
    }

    fun oppdaterNavAnsatte() {
        navAnsattUpdater.oppdaterAlle()
    }

    fun oppdaterNavEnheter() {
        navEnhetUpdateJob.update()
    }

    fun synkroniserKrr(
        offset: Int,
        batchSize: Int,
    ) {
        val personidenter =
            navBrukerRepository
                .getPersonidenter(
                    offset = offset,
                    limit = batchSize,
                    notSyncedSince = LocalDateTime.now().minusDays(3),
                ).toSet()

        navBrukerService.syncKontaktinfoBulk(personidenter)
    }

    fun oppdaterManglendeKontaktinfo(batchSize: Int) {
        val jobName = "oppdater-manglende-kontaktinfo"
        var batchNumber = 1
        var personidenter: Set<String>
        var sistePersonident: String? = null

        do {
            personidenter = navBrukerRepository.getPersonidenterMedManglendeKontaktinfo(sistePersonident, batchSize).toSet()

            if (personidenter.isNotEmpty()) {
                log.info("Processing $jobName batch #$batchNumber count=${personidenter.size}")
                navBrukerService.syncKontaktinfoBulk(personidenter)
            }
            batchNumber++
            sistePersonident = personidenter.lastOrNull()
        } while (personidenter.isNotEmpty())

        log.info("No more data after batch $batchNumber. Done.")
    }

    fun synkroniserKrrForPerson(personident: String) {
        val navBruker =
            navBrukerRepository.get(personident)
                ?: throw IllegalArgumentException("Fant ikke Nav-bruker")
        navBrukerService.oppdaterKontaktinformasjon(navBruker)
    }

    fun republiserNavBrukereMedNyIdent() {
        val personidenter = personidentRepository.getPersonIderMedFlerePersonidenter()
        log.info("Starter republisering av Nav-brukere med ny ident. Antall: ${personidenter.size}")

        personidenter.forEach {
            navBrukerRepository.getByPersonId(it)?.let { navBrukerDbo ->
                republiser { kafkaProducerService.publiserNavBruker(navBrukerDbo) }
            }
        }
        log.info("Ferdig med republisering av Nav-brukere med ny ident")
    }

    // KafkaProducerService krever en transaksjon. Én kort transaksjon per melding gir samme
    // oppførsel som før: meldinger som allerede er lagt i outboxen blir liggende hvis en senere feiler.
    private fun republiser(block: () -> Unit) {
        transactionTemplate.executeWithoutResult { block() }
    }

    private fun batchHandterNavBrukere(
        startFromOffset: Int,
        batchSize: Int,
        action: (navBruker: NavBrukerDbo) -> Unit,
    ) {
        var currentOffset = startFromOffset
        var navBrukerDbos: List<NavBrukerDbo>

        val start = Instant.now()
        var totalHandled = 0

        do {
            navBrukerDbos = navBrukerRepository.getAllNavBrukere(currentOffset, batchSize)
            navBrukerDbos.forEach { action(it) }
            totalHandled += navBrukerDbos.size
            currentOffset += batchSize
            log.info("Republisering av nav-brukere - offset: $currentOffset, total handled: $totalHandled")
        } while (navBrukerDbos.isNotEmpty())

        val duration = Duration.between(start, Instant.now())
        if (totalHandled > 0) {
            log.info(
                "batchHandterNavBrukere handled $totalHandled Nav-bruker records in ${duration.toSeconds()}.${duration.toMillisPart()} seconds.",
            )
        }
    }

    private fun batchHandterNavBrukereByModifiedBefore(
        modifiedBefore: LocalDate,
        batchSize: Int,
        startAfterId: UUID?,
        action: (navBruker: NavBrukerDbo) -> Unit,
    ) {
        var lastId: UUID? = startAfterId
        var navBrukerDbos: List<NavBrukerDbo>

        val start = Instant.now()
        var totalHandled = 0

        do {
            navBrukerDbos = navBrukerRepository.getAllNavBrukere(batchSize, modifiedBefore, lastId)
            navBrukerDbos.forEach { action(it) }
            totalHandled += navBrukerDbos.size
            lastId = navBrukerDbos.lastOrNull()?.id
            log.info("Handled nav-bruker batch $totalHandled records. lastId $lastId")
        } while (navBrukerDbos.isNotEmpty())

        val duration = Duration.between(start, Instant.now())
        if (totalHandled > 0) {
            log.info(
                "batchHandterNavBrukereByModifiedBefore handled $totalHandled Nav-bruker records in ${duration.toSeconds()}.${duration.toMillisPart()} seconds.",
            )
        }
    }

    private fun oppdaterAdresseHvisManglerOgRepubliser(
        modifiedBefore: LocalDateTime,
        batchSize: Int,
        startAfterId: UUID?,
    ) {
        var lastId: UUID? = startAfterId
        var navbrukere: List<NavBrukerDbo>

        do {
            navbrukere = navBrukerRepository.getAllUtenAdresse(batchSize, modifiedBefore, lastId)
            val personidenter = navbrukere.map { it.person.personident }.toSet()
            navBrukerService.oppdaterAdresse(personidenter)
            lastId = navbrukere.lastOrNull()?.id
            log.info("Oppdaterte adresse for ${navbrukere.size} personer. Siste Nav-bruker-id: $lastId")
        } while (navbrukere.isNotEmpty())
    }
}
