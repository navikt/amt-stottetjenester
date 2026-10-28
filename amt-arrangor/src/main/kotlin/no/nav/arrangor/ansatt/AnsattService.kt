package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.kafka.ProducerService
import no.nav.arrangor.kafka.model.DeltakerStatusType
import no.nav.arrangor.metrics.MetricEvent
import no.nav.arrangor.utils.executeInTransactionAndRequireResult
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattService(
    private val personClient: PersonClient,
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorSyncService: AnsattArrangorSyncService,
    private val rolleService: AnsattRolleService,
    private val producerService: ProducerService,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    private val arrangorService: ArrangorService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID): Ansatt? = ansattRepository
        .get(id)
        ?.let { getAndMaybeUpdateAnsatt(it) }

    fun get(personident: String): Ansatt? = ansattRepository
        .get(personident)
        ?.let { getAndMaybeUpdateAnsatt(it) }
        ?: opprettAnsatt(personident)

    fun getAnsattIdForPersonident(personident: String): UUID? = ansattRepository.getIdForPersonident(personident)

    fun setKoordinatorForDeltakerliste(
        personident: String,
        arrangorId: UUID,
        deltakerlisteId: UUID,
    ): Ansatt {
        val (ansatt, arrangor) = hentKoordinatorOgArrangor(personident, arrangorId)

        val eksisterendeDeltakerliste = arrangor.koordinator
            .find { it.deltakerlisteId == deltakerlisteId }

        return when {
            eksisterendeDeltakerliste?.erGyldig() == true -> {
                logger.info("Deltakerliste med id $deltakerlisteId er allerede lagt til")
                getAndMaybeUpdateAnsatt(ansatt)
            }

            else -> opprettKoordinatorTilgang(arrangor, deltakerlisteId, ansatt)
        }
    }

    private fun opprettKoordinatorTilgang(
        arrangor: ArrangorDbo,
        deltakerlisteId: UUID,
        ansatt: AnsattDbo,
    ): Ansatt = transactionTemplate.executeInTransactionAndRequireResult {
        val nyKoordinator = KoordinatorsDeltakerlisteDbo(deltakerlisteId)
        val oppdatertDeltakerlisterForArrangor = arrangor.koordinator + nyKoordinator
        val oppdatertAnsattDbo = oppdaterAnsattArrangorer(
            ansattDbo = ansatt,
            oppdatertArrangor = arrangor.copy(koordinator = oppdatertDeltakerlisterForArrangor),
        )

        val oppdatertAnsatt = mapToAnsatt(
            ansattDbo = ansattArrangorSyncService.insertKoordinator(
                ansatt = oppdatertAnsattDbo,
                arrangorId = arrangor.arrangorId,
                koordinator = nyKoordinator,
            ),
        )

        producerService.publishAnsatt(ansatt = oppdatertAnsatt)
        eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.KOORDINATOR_ADDED))
        logger.info("Ansatt ${oppdatertAnsattDbo.id} ble koordinator for deltakerliste $deltakerlisteId")

        oppdatertAnsatt
    }

    fun fjernKoordinatorForDeltakerliste(
        personident: String,
        arrangorId: UUID,
        deltakerlisteId: UUID,
    ): Ansatt {
        val (ansattDbo, arrangor) = hentKoordinatorOgArrangor(
            personident = personident,
            arrangorId = arrangorId,
        )

        return fjernDeltakerlisteTilgang(
            arrangor = arrangor,
            deltakerlisteId = deltakerlisteId,
            ansattDbo = ansattDbo,
            fjerningstidspunkt = ZonedDateTime.now(),
        )
    }

    private fun fjernDeltakerlisteTilgang(
        arrangor: ArrangorDbo,
        deltakerlisteId: UUID,
        ansattDbo: AnsattDbo,
        fjerningstidspunkt: ZonedDateTime,
    ): Ansatt {
        val deaktivertAnsatt = arrangor.koordinator
            .find { it.deltakerlisteId == deltakerlisteId && it.erGyldig() }
            ?.let { koordinator ->
                transactionTemplate.executeInTransactionAndRequireResult {
                    koordinator.gyldigTil = fjerningstidspunkt
                    val oppdatert = ansattArrangorSyncService.deaktiverKoordinator(
                        ansatt = ansattDbo,
                        arrangorId = arrangor.arrangorId,
                        koordinator = koordinator,
                    )
                    producerService.publishAnsatt(ansatt = mapToAnsatt(oppdatert))
                    eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.KOORDINATOR_REMOVED))
                    logger.info("Ansatt ${ansattDbo.id} mistet koordinator for deltakerliste $deltakerlisteId")
                    oppdatert
                }
            }

        return getAndMaybeUpdateAnsatt(deaktivertAnsatt ?: ansattDbo)
    }

    private fun hentKoordinatorOgArrangor(
        personident: String,
        arrangorId: UUID,
    ): Pair<AnsattDbo, ArrangorDbo> {
        val ansattDbo = ansattRepository
            .get(personident)
            ?: throw NoSuchElementException("Ansatt finnes ikke")

        val arrangor = finnArrangorMedRolle(
            ansattDbo = ansattDbo,
            arrangorId = arrangorId,
            rolle = AnsattRolle.KOORDINATOR,
        ).getOrThrow()

        return Pair(ansattDbo, arrangor)
    }

    private fun finnArrangorMedRolle(
        ansattDbo: AnsattDbo,
        arrangorId: UUID,
        rolle: AnsattRolle,
    ): Result<ArrangorDbo> {
        val arrangor = ansattDbo.arrangorer
            .find { it.arrangorId == arrangorId }
            ?: return Result.failure(IllegalArgumentException("Ansatt har ikke tilgang til arrangør med id $arrangorId"))

        if (arrangor.roller.none { it.erGyldig() && it.rolle == rolle }) {
            return Result.failure(
                IllegalArgumentException("Ansatt har ikke ${rolle.name.lowercase()}tilgang for arrangør med id $arrangorId"),
            )
        }
        return Result.success(arrangor)
    }

    fun oppdaterVeiledereForDeltaker(
        personident: String,
        deltakerId: UUID,
        request: AnsattAPI.OppdaterVeiledereForDeltakerRequest,
    ) {
        val ansattDbo = ansattRepository
            .get(personident)
            ?: throw NoSuchElementException("Ansatt finnes ikke")

        finnArrangorMedRolle(
            ansattDbo = ansattDbo,
            arrangorId = request.arrangorId,
            rolle = AnsattRolle.KOORDINATOR,
        ).getOrThrow()

        val ansatteSomFjernes = ansattRepository.getAnsatte(request.veilederSomFjernes.map { it.ansattId })
        val fjerningstidspunkt = ZonedDateTime.now()

        ansatteSomFjernes.forEach { ansatt ->
            fjernVeileder(
                ansattDbo = ansatt,
                arrangorId = request.arrangorId,
                deltakerId = deltakerId,
                type = request.veilederSomFjernes.find { it.ansattId == ansatt.id }?.type
                    ?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!"),
                fjerningstidspunkt = fjerningstidspunkt,
            )
        }

        val ansatteSomLeggesTil = ansattRepository.getAnsatte(request.veilederSomLeggesTil.map { it.ansattId })

        ansatteSomLeggesTil.forEach { ansatt ->
            setVeileder(
                ansattDbo = ansatt,
                arrangorId = request.arrangorId,
                deltakerId = deltakerId,
                type = request.veilederSomLeggesTil.find { it.ansattId == ansatt.id }?.type
                    ?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!"),
            )
        }
        logger.info("Oppdatert veiledere for deltaker $deltakerId")
    }

    private fun setVeileder(
        ansattDbo: AnsattDbo,
        arrangorId: UUID,
        deltakerId: UUID,
        type: VeilederType,
    ) {
        val ansattArrangor = finnArrangorMedRolle(
            ansattDbo = ansattDbo,
            arrangorId = arrangorId,
            rolle = AnsattRolle.VEILEDER,
        ).getOrThrow()

        val eksisterendeVeilederRelasjon = ansattArrangor.veileder.find { it.deltakerId == deltakerId && it.veilederType == type }
        if (eksisterendeVeilederRelasjon?.erGyldig() == true) {
            logger.info("Ansatt er allerede veileder for deltaker med id $deltakerId")
            return
        }

        val nyVeileder = VeilederDeltakerDbo(deltakerId, type)
        val oppdatertAnsattDbo = oppdaterAnsattArrangorer(
            ansattDbo = ansattDbo,
            oppdatertArrangor = ansattArrangor.copy(veileder = ansattArrangor.veileder + nyVeileder),
        )

        transactionTemplate.executeWithoutResult {
            val oppdatertAnsatt = mapToAnsatt(
                ansattArrangorSyncService.insertVeileder(
                    ansatt = oppdatertAnsattDbo,
                    arrangorId = arrangorId,
                    veileder = nyVeileder,
                ),
            )
            producerService.publishAnsatt(oppdatertAnsatt)
            eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.VEILEDER_ADDED))
            logger.info("Ansatt ${ansattDbo.id} ble $type for deltaker $deltakerId")
        }
    }

    private fun oppdaterAnsattArrangorer(
        ansattDbo: AnsattDbo,
        oppdatertArrangor: ArrangorDbo,
    ): AnsattDbo {
        val oppdaterteArrangorer = ansattDbo.arrangorer
            .filter { it.arrangorId != oppdatertArrangor.arrangorId }
            .plus(oppdatertArrangor)

        return ansattDbo.copy(arrangorer = oppdaterteArrangorer)
    }

    fun fjernVeileder(
        ansattDbo: AnsattDbo,
        arrangorId: UUID,
        deltakerId: UUID,
        type: VeilederType,
        fjerningstidspunkt: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val ansattArrangor = ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
        if (ansattArrangor == null) {
            logger.warn("Ansatt ${ansattDbo.id} har ingen tilganger hos arrangør $arrangorId, ingen deltakere å fjerne")
            return
        }

        ansattArrangor.veileder
            .find { it.deltakerId == deltakerId && it.veilederType == type && it.erGyldig() }
            ?.let { veileder ->
                transactionTemplate.executeWithoutResult {
                    veileder.gyldigTil = fjerningstidspunkt
                    val oppdatertAnsatt = mapToAnsatt(
                        ansattArrangorSyncService.deaktiverVeiledere(
                            ansatt = ansattDbo,
                            arrangorId = arrangorId,
                            veiledere = listOf(veileder),
                        ),
                    )
                    producerService.publishAnsatt(oppdatertAnsatt)
                    eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.VEILEDER_REMOVED))
                }
            }

        logger.info("Ansatt ${ansattDbo.id} mistet veilederrolle for $deltakerId")
    }

    fun opprettAnsatt(personIdent: String): Ansatt? {
        val altinnRoller = rolleService.getRollerFraAltinn(personIdent)
        if (altinnRoller.isEmpty()) {
            logger.info("Bruker uten rettigheter i Altinn har logget seg inn")
            return null
        }
        val person = personClient.hentPersonalia(personIdent).getOrThrow()

        val arrangorer = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

        return transactionTemplate.executeInTransactionAndRequireResult {
            val ansattDbo = ansattArrangorSyncService.opprettAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personident = personIdent,
                    personId = person.id,
                    fornavn = person.fornavn,
                    mellomnavn = person.mellomnavn,
                    etternavn = person.etternavn,
                    arrangorer = arrangorer,
                ),
            )
            logger.info("Opprettet ny ansatt og lagret roller for ansattId ${ansattDbo.id}")
            mapToAnsatt(ansattDbo).also { producerService.publishAnsatt(ansatt = it) }
        }
    }

    fun oppdaterAnsattesRoller() {
        ansattRepository
            .getToSynchronize(
                maxSize = 50,
                synchronizedBefore = LocalDateTime.now().minusDays(7),
            ).forEach { oppdaterRoller(it) }
    }

    private fun getAndMaybeUpdateAnsatt(ansattDbo: AnsattDbo): Ansatt {
        val shouldSynchronize = ansattDbo.lastSynchronized.isBefore(LocalDateTime.now().minusHours(1))

        return if (shouldSynchronize) {
            oppdaterRoller(ansattDbo)
        } else {
            mapToAnsatt(ansattDbo)
        }
    }

    /** Tar bare imot [AnsattDbo] lest fra [AnsattRepository], slik at arrangørrelasjonene er med. */
    private fun oppdaterRoller(ansattDbo: AnsattDbo): Ansatt {
        val ansattDboMedOppdaterteRoller = rolleService.getAnsattDboMedOppdaterteRoller(ansattDbo)

        return transactionTemplate.executeInTransactionAndRequireResult {
            val oppdatertAnsattDbo = ansattArrangorSyncService.oppdaterRoller(ansattDboMedOppdaterteRoller)
            mapToAnsatt(oppdatertAnsattDbo)
                .also { if (ansattDboMedOppdaterteRoller.isUpdated) producerService.publishAnsatt(ansatt = it) }
        }
    }

    fun getAll(
        offset: Int,
        limit: Int,
    ): List<Ansatt> = ansattRepository.getAll(offset, limit).map { mapToAnsatt(it) }

    private fun mapToAnsatt(ansattDbo: AnsattDbo): Ansatt = Ansatt(
        id = ansattDbo.id,
        personalia = ansattDbo.toPersonalia(),
        arrangorer = mapToTilknyttetArrangorListe(ansattDbo.arrangorer),
    )

    private fun mapToTilknyttetArrangorListe(arrangorDboListe: List<ArrangorDbo>): List<TilknyttetArrangor> {
        if (arrangorDboListe.isEmpty()) {
            return emptyList()
        }
        val unikeArrangorIder = arrangorDboListe.map { it.arrangorId }.distinct()
        val arrangorer = arrangorService.getArrangorerMedOverordnetArrangor(unikeArrangorIder)

        return arrangorDboListe.mapNotNull { arrangorDbo ->
            val arrangor = arrangorer.find { it.id == arrangorDbo.arrangorId }
            if (arrangor == null || arrangorDbo.roller.none { it.erGyldig() }) {
                return@mapNotNull null
            }
            TilknyttetArrangor(
                arrangorId = arrangorDbo.arrangorId,
                arrangor = Arrangor(
                    id = arrangor.id,
                    navn = arrangor.navn,
                    organisasjonsnummer = arrangor.organisasjonsnummer,
                    overordnetArrangorId = arrangor.overordnetArrangor?.id,
                ),
                overordnetArrangor = arrangor.overordnetArrangor,
                roller = arrangorDbo.roller.filter { it.erGyldig() }.map { it.rolle },
                veileder = arrangorDbo.veileder.filter { it.erGyldig() }.map {
                    Veileder(
                        deltakerId = it.deltakerId,
                        type = it.veilederType,
                    )
                },
                koordinator = arrangorDbo.koordinator.filter { it.erGyldig() }.map { it.deltakerlisteId },
            )
        }
    }

    fun deaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        deaktiveringsdato: ZonedDateTime,
        status: DeltakerStatusType?,
    ) {
        // REQUIRED blir med i ConsumerService-transaksjonen eller åpner en transaksjon ved selvstendige kall.
        transactionTemplate.executeWithoutResult {
            val endredeAnsattIder = ansattArrangorSyncService.deaktiverVeiledereForDeltaker(
                deltakerId = deltakerId,
                deaktiveringsdato = deaktiveringsdato,
            )
            val ansatteEndret = ansattRepository.getAnsatte(endredeAnsattIder)
            ansatteEndret.forEach { producerService.publishAnsatt(ansatt = mapToAnsatt(it)) }

            if (ansatteEndret.isNotEmpty()) {
                logger.info("Deaktiverte veiledere for deltaker $deltakerId med status ${status?.name ?: "slettet"}")
            }
        }
    }

    fun maybeReaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        status: DeltakerStatusType,
    ) {
        // REQUIRED blir med i ConsumerService-transaksjonen eller åpner en transaksjon ved selvstendige kall.
        transactionTemplate.executeWithoutResult {
            val terskel = ZonedDateTime.now()
            val endredeAnsattIder = ansattArrangorSyncService.maybeReaktiverVeiledereForDeltaker(
                deltakerId = deltakerId,
                terskel = terskel,
            )
            val ansatteEndret = ansattRepository.getAnsatte(endredeAnsattIder)
            ansatteEndret.forEach { producerService.publishAnsatt(ansatt = mapToAnsatt(it)) }

            if (ansatteEndret.isNotEmpty()) {
                logger.info("Reaktiverte veiledere ${ansatteEndret.size} for deltaker $deltakerId med status ${status.name}")
            }
        }
    }

    fun fjernTilgangerHosArrangor(
        deltakerlisteId: UUID,
        deltakerIder: List<UUID>,
        arrangorId: UUID,
    ) {
        val ansatte = ansattRepository.getAnsatteHosArrangor(arrangorId)
        val fjerningstidspunkt = ZonedDateTime.now()

        for (ansatt in ansatte) {
            fjernGammelKoordinatorTilgang(
                ansatt = ansatt,
                gammelArrangorId = arrangorId,
                deltakerlisteId = deltakerlisteId,
                fjerningstidspunkt = fjerningstidspunkt,
            )

            finnArrangorMedRolle(ansatt, arrangorId, AnsattRolle.VEILEDER).onSuccess { arrangor ->
                val fjernedeTilganger = fjernGamleVeilederTilganger(
                    arrangor = arrangor,
                    deltakerIder = deltakerIder,
                    fjerningstidspunkt = fjerningstidspunkt,
                )

                if (fjernedeTilganger.isNotEmpty()) {
                    transactionTemplate.executeWithoutResult {
                        val oppdatertAnsatt = mapToAnsatt(
                            ansattArrangorSyncService.deaktiverVeiledere(
                                ansatt = ansatt,
                                arrangorId = arrangorId,
                                veiledere = fjernedeTilganger,
                            ),
                        )
                        producerService.publishAnsatt(ansatt = oppdatertAnsatt)
                        eventPublisher.publishEvent(
                            MetricEvent(
                                name = MetricEvent.MetricName.VEILEDER_REMOVED,
                                count = fjernedeTilganger.size,
                            ),
                        )
                        logger.info("Ansatt ${ansatt.id} mistet veilederroller for deltakere på deltakerlisten $deltakerlisteId")
                    }
                }
            }
        }
    }

    private fun fjernGamleVeilederTilganger(
        arrangor: ArrangorDbo,
        deltakerIder: List<UUID>,
        fjerningstidspunkt: ZonedDateTime,
    ): List<VeilederDeltakerDbo> {
        val deltakere = arrangor.veileder.filter { it.erGyldig() && it.deltakerId in deltakerIder }

        deltakere.forEach { it.gyldigTil = fjerningstidspunkt }
        return deltakere
    }

    private fun fjernGammelKoordinatorTilgang(
        ansatt: AnsattDbo,
        gammelArrangorId: UUID,
        deltakerlisteId: UUID,
        fjerningstidspunkt: ZonedDateTime,
    ) {
        finnArrangorMedRolle(
            ansattDbo = ansatt,
            arrangorId = gammelArrangorId,
            rolle = AnsattRolle.KOORDINATOR,
        ).onSuccess { arrangor ->
            fjernDeltakerlisteTilgang(
                arrangor = arrangor,
                deltakerlisteId = deltakerlisteId,
                ansattDbo = ansatt,
                fjerningstidspunkt = fjerningstidspunkt,
            )
        }
    }
}
