package no.nav.arrangor.kafka

import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.deltaker.DeltakerRepository
import no.nav.arrangor.kafka.model.AVSLUTTENDE_STATUSER
import no.nav.arrangor.kafka.model.AnsattPersonaliaDto
import no.nav.arrangor.kafka.model.Deltaker
import no.nav.arrangor.kafka.model.SKJULES_ALLTID_STATUSER
import no.nav.arrangor.kafka.model.VirksomhetDto
import no.nav.arrangor.metrics.MetricEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class ConsumerService(
    private val ansattRepository: AnsattRepository,
    private val ansattService: AnsattService,
    private val arrangorRepository: ArrangorRepository,
    private val enhetsregisterClient: EnhetsregisterClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val producerService: ProducerService,
    private val deltakerRepository: DeltakerRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleVirksomhetEndring(virksomhetDto: VirksomhetDto?) {
        // tombstone ignoreres
        if (virksomhetDto == null) return

        // consumer er for oppdatering av eksisterende arrangør, om en slik ikke finnes, returner
        val eksisterendeArrangor = arrangorRepository.get(virksomhetDto.organisasjonsnummer) ?: return

        val overordnetOrgNr = virksomhetDto.overordnetEnhetOrganisasjonsnummer
        val lagretOverordnetArrangor = overordnetOrgNr?.let(arrangorRepository::get)

        // Hent eventuell manglende overordnet arrangør før transaksjonen, så HTTP-kallet ikke holder den åpen.
        val nyOverordnetArrangor = overordnetOrgNr
            ?.takeIf { lagretOverordnetArrangor == null }
            ?.let { innerOverordnetOrgNr ->
                logger.warn(
                    "Fant ikke overordnet arrangør i db for orgnummer $innerOverordnetOrgNr, henter overordnet arrangør for arrangør ${eksisterendeArrangor.id}",
                )

                enhetsregisterClient
                    .hentVirksomhet(innerOverordnetOrgNr)
                    .getOrNull()
                    ?.let {
                        ArrangorRepository.ArrangorDbo(
                            id = UUID.randomUUID(),
                            navn = it.navn,
                            organisasjonsnummer = it.organisasjonsnummer,
                            overordnetArrangorId = null,
                        )
                    }.also {
                        if (it == null) logger.warn("Fant ikke overordnet arrangør for orgnummer $overordnetOrgNr i amt-enhetsregister")
                    }
            }

        // Lagre forelder, arrangør og tilhørende outbox-meldinger atomisk
        transactionTemplate.executeWithoutResult {
            val overordnetArrangorId = overordnetOrgNr?.let {
                getOverordnetArrangorId(
                    overordnetEnhetOrganisasjonsnummer = it,
                    arrangor = eksisterendeArrangor,
                    nyOverordnetArrangor = nyOverordnetArrangor,
                )
            }

            val oppdatertArrangor = eksisterendeArrangor.copy(
                navn = virksomhetDto.navn,
                organisasjonsnummer = virksomhetDto.organisasjonsnummer,
                overordnetArrangorId = overordnetArrangorId,
            )

            // lagre og publiser arrangør kun hvis endringer
            if (eksisterendeArrangor != oppdatertArrangor) {
                val oppdatertArrangorFraDatabase = arrangorRepository.insertOrUpdate(oppdatertArrangor)
                producerService.publishArrangor(oppdatertArrangorFraDatabase.toDomain())
                eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.ARRANGOR_CHANGED))
                logger.info("Oppdatert arrangør med id ${oppdatertArrangorFraDatabase.id}")
            } else {
                logger.info("Arrangør med id ${eksisterendeArrangor.id} er uendret")
            }

            eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.VIRKSOMHET_EVENT_CONSUMED))
        }
    }

    /**
     * Finner ID-en til overordnet arrangør, eventuelt ved å opprette den forhåndshentede arrangøren.
     *
     * Har sideeffekter: kan lagre arrangøren, legge en melding i Kafka-outbox og publisere et metric-event.
     * Kalles fra [handleVirksomhetEndring] innenfor samme transaksjon.
     */
    private fun getOverordnetArrangorId(
        overordnetEnhetOrganisasjonsnummer: String,
        arrangor: ArrangorRepository.ArrangorDbo,
        nyOverordnetArrangor: ArrangorRepository.ArrangorDbo?,
    ): UUID? {
        // Slå opp på nytt i transaksjonen; forelderen kan ha blitt opprettet mens http-kallet pågikk
        val overordnetArrangor = arrangorRepository.get(overordnetEnhetOrganisasjonsnummer)
            ?: nyOverordnetArrangor?.let { arrangorDbo ->
                arrangorRepository
                    .insertOrUpdate(arrangorDbo)
                    .also { opprettet ->
                        logger.info("Opprettet ny overordnet arrangør med id ${opprettet.id}")
                        producerService.publishArrangor(opprettet.toDomain())
                        eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.ARRANGOR_CHANGED))
                    }
            }

        if (overordnetArrangor?.id != arrangor.overordnetArrangorId) {
            overordnetArrangor?.let {
                logger.info("Arrangør ${arrangor.id} har fått ny overordnet arrangør med id ${it.id}")
            }
        }

        return overordnetArrangor?.id
    }

    fun handleAnsattPersonalia(ansattPersonalia: AnsattPersonaliaDto) {
        val ansatt = ansattRepository.getByPersonId(ansattPersonalia.id)
            ?: return logger.warn("Mottok personalia men fant ikke ansatt med personId ${ansattPersonalia.id}")

        if (harPersonaliaEndringer(ansatt, ansattPersonalia)) {
            val oppdatert = ansattRepository.updatePersonalia(
                ansattId = ansatt.id,
                personident = ansattPersonalia.personident,
                fornavn = ansattPersonalia.fornavn,
                mellomnavn = ansattPersonalia.mellomnavn,
                etternavn = ansattPersonalia.etternavn,
            )

            if (!oppdatert) {
                logger.warn("Ansatt ${ansatt.id} ble fjernet før personalia kunne oppdateres")
                return
            }

            logger.info("Oppdaterte personalia for ansatt ${ansatt.id}")
        }
    }

    private fun harPersonaliaEndringer(
        ansatt: AnsattDbo,
        ansattPersonalia: AnsattPersonaliaDto,
    ): Boolean = ansatt.personident != ansattPersonalia.personident ||
        ansatt.fornavn != ansattPersonalia.fornavn ||
        ansatt.mellomnavn != ansattPersonalia.mellomnavn ||
        ansatt.etternavn != ansattPersonalia.etternavn

    fun handleDeltakerEndring(
        id: UUID,
        deltaker: Deltaker?,
    ) {
        if (skalOppdatereVeiledereForDeltaker(id, deltaker)) {
            // Hold deltakerstatus, veiledertilganger og outbox-meldinger konsistente ved feil.
            transactionTemplate.executeWithoutResult {
                if (deltaker == null || deltaker.status.type in SKJULES_ALLTID_STATUSER || deltaker.status.type in AVSLUTTENDE_STATUSER) {
                    val deaktiveringsdato = LocalDateTime.now().plusDays(50).atZone(ZoneId.systemDefault())

                    // Deltakere fjernes fra deltakeroversikten 40 dager etter avsluttende status er satt,
                    // så veiledere må ikke deaktiveres før den datoen er passert. For statuser som skjules umiddelbart deaktiverer vi
                    // om 50 dager for litt sikkerhetsmargin i tilfelle deltaker blir aktiv igjen.
                    ansattService.deaktiverVeiledereForDeltaker(
                        deltakerId = id,
                        deaktiveringsdato = deaktiveringsdato,
                        status = deltaker?.status?.type,
                    )
                } else {
                    ansattService.maybeReaktiverVeiledereForDeltaker(id, deltaker.status.type)
                }
                deltaker?.let { deltakerRepository.insertOrUpdate(it) }
            }
        }
    }

    private fun skalOppdatereVeiledereForDeltaker(
        id: UUID,
        deltaker: Deltaker?,
    ): Boolean {
        if (deltaker == null) {
            logger.info("Oppdaterer veilederkoblinger for tombstonet deltaker $id")
            return true
        }
        val lagretDeltaker = deltakerRepository.get(id)
        if (lagretDeltaker == null) {
            logger.info("Oppdaterer veilederkoblinger for deltaker som ikke er lagret $id")
            return true
        } else if (deltaker.status.type != lagretDeltaker.status.type) {
            logger.info("Oppdaterer veilederkoblinger for deltaker som har endret status $id")
            return true
        } else {
            logger.info("Deltaker $id har ikke endret status, ignorerer")
            return false
        }
    }
}
