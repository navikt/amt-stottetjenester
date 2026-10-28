package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.kafka.ProducerService
import no.nav.arrangor.metrics.MetricEvent
import no.nav.arrangor.utils.executeInTransactionAndRequireResult
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class ArrangorService(
    private val arrangorRepository: ArrangorRepository,
    private val enhetsregisterClient: EnhetsregisterClient,
    private val producerService: ProducerService,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getOrCreate(orgNr: String): Arrangor {
        val arrangor = arrangorRepository.get(orgNr)

        if (arrangor != null) return arrangor.toDomain()

        logger.info("Arrangør for orgnummer $orgNr mangler, oppretter arrangør..")
        return insertArrangor(orgNr).toDomain()
    }

    fun getOrCreate(orgnumre: List<String>): List<Arrangor> {
        val lagredeArrangorer = arrangorRepository
            .getArrangorerMedOrgnumre(orgnumre)
            .map { it.toDomain() }

        val orgnummerSomMangler = orgnumre
            .filterNot { orgnummer -> lagredeArrangorer.any { it.organisasjonsnummer == orgnummer } }

        val nyeArrangorer = orgnummerSomMangler.map { insertArrangor(it).toDomain() }
        return lagredeArrangorer + nyeArrangorer
    }

    fun getArrangorMedOverordnetArrangor(orgnr: String): ArrangorMedOverordnetArrangor {
        val arrangor = getOrCreate(orgnr)
        val overordnetArrangor = arrangor.overordnetArrangorId?.let { arrangorRepository.get(it)?.toDomain() }
        return ArrangorMedOverordnetArrangor(
            id = arrangor.id,
            navn = arrangor.navn,
            organisasjonsnummer = arrangor.organisasjonsnummer,
            overordnetArrangor = overordnetArrangor,
        )
    }

    fun getArrangorMedOverordnetArrangor(id: UUID): ArrangorMedOverordnetArrangor? {
        val arrangor = arrangorRepository.get(id).let { it?.toDomain() } ?: return null

        val overordnetArrangor = arrangor.overordnetArrangorId?.let { arrangorRepository.get(it)?.toDomain() }
        return ArrangorMedOverordnetArrangor(
            id = arrangor.id,
            navn = arrangor.navn,
            organisasjonsnummer = arrangor.organisasjonsnummer,
            overordnetArrangor = overordnetArrangor,
        )
    }

    fun getArrangorerMedOverordnetArrangor(ider: List<UUID>): List<ArrangorMedOverordnetArrangor> {
        val arrangorer = arrangorRepository.getArrangorerMedIder(ider)
        val unikeOverordnedeArrangorIder = arrangorer.mapNotNull { it.overordnetArrangorId }.distinct()
        val overordnedeArrangorer = arrangorRepository.getArrangorerMedIder(unikeOverordnedeArrangorIder)

        return arrangorer.map { arrangorDbo ->
            ArrangorMedOverordnetArrangor(
                id = arrangorDbo.id,
                navn = arrangorDbo.navn,
                organisasjonsnummer = arrangorDbo.organisasjonsnummer,
                overordnetArrangor = arrangorDbo.overordnetArrangorId
                    ?.let { overordnetArrangorId ->
                        val overordnetArrangor = overordnedeArrangorer.find { overordnetArrangorId == it.id }
                        overordnetArrangor?.let {
                            Arrangor(
                                id = it.id,
                                navn = it.navn,
                                organisasjonsnummer = it.organisasjonsnummer,
                                overordnetArrangorId = it.overordnetArrangorId,
                            )
                        }
                    },
            )
        }
    }

    private fun insertArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
        val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrThrow()
        val overordnetArrangor = virksomhet.toOverordnetArrangorDbo()

        return transactionTemplate.executeInTransactionAndRequireResult {
            val lagretOverordnetArrangor = overordnetArrangor?.let {
                arrangorRepository
                    .insertOrUpdate(it)
                    .also { arrangor ->
                        producerService.publishArrangor(arrangor.toDomain())
                        eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.ARRANGOR_CHANGED))
                    }
            }

            val arrangor = arrangorRepository.insertOrUpdate(
                ArrangorRepository.ArrangorDbo(
                    id = UUID.randomUUID(),
                    navn = virksomhet.navn,
                    organisasjonsnummer = virksomhet.organisasjonsnummer,
                    overordnetArrangorId = lagretOverordnetArrangor?.id,
                ),
            )
            producerService.publishArrangor(arrangor.toDomain())
            eventPublisher.publishEvent(MetricEvent(MetricEvent.MetricName.ARRANGOR_CHANGED))
            arrangor
        }
    }

    private fun Virksomhet.toOverordnetArrangorDbo(): ArrangorRepository.ArrangorDbo? {
        if (overordnetEnhetOrganisasjonsnummer == null || overordnetEnhetNavn == null) return null
        return ArrangorRepository.ArrangorDbo(
            id = UUID.randomUUID(),
            navn = overordnetEnhetNavn,
            organisasjonsnummer = overordnetEnhetOrganisasjonsnummer,
            overordnetArrangorId = null,
        )
    }
}
