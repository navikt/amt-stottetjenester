package no.nav.amt.person.service.navenhet

import no.nav.amt.person.service.clients.norg.NorgClient
import no.nav.amt.person.service.clients.oppfolgingskontor.OppfolgingskontorClient
import no.nav.amt.person.service.config.TeamLogs
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class NavEnhetService(
    private val navEnhetRepository: NavEnhetRepository,
    private val norgClient: NorgClient,
    private val oppfolgingskontorClient: OppfolgingskontorClient,
    private val kafkaProducerService: KafkaProducerService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentNavEnhetForBruker(personident: String): NavEnhetDbo? {
        val oppfolgingsenhetId = oppfolgingskontorClient.hentKontorForBruker(personident)?.kontorId ?: return null

        return hentEllerOpprettNavEnhet(oppfolgingsenhetId)
            .also {
                if (it == null) {
                    TeamLogs.warn("Bruker med personident=$personident har enhetId=$oppfolgingsenhetId som ikke finnes i norg")
                }
            }
    }

    fun hentEllerOpprettNavEnhet(enhetId: String): NavEnhetDbo? = navEnhetRepository.get(enhetId) ?: opprettEnhet(enhetId)

    private fun opprettEnhet(enhetId: String): NavEnhetDbo? {
        val norgEnhet = norgClient.hentNavEnhet(enhetId) ?: return null

        val enhet = NavEnhetDbo(
            id = UUID.randomUUID(),
            enhetId = enhetId,
            navn = norgEnhet.navn,
        )

        transactionTemplate.executeWithoutResult {
            navEnhetRepository.insert(enhet)
            kafkaProducerService.publiserNavEnhet(enhet)
        }

        return enhet
    }

    fun oppdaterNavEnheter(enheter: List<NavEnhetDbo>) {
        val oppdaterteEnheter =
            norgClient.hentNavEnheter(enheter.map { it.enhetId }).associateBy { it.enhetNr }

        log.info(
            "Hentet ${enheter.size} enheter fra Norg for oppdatering av navn. Norg returnerte ${oppdaterteEnheter.size} enheter.",
        )

        enheter.forEach { opprinneligEnhet ->
            val oppdatertEnhet = oppdaterteEnheter[opprinneligEnhet.enhetId]

            if (oppdatertEnhet != null && oppdatertEnhet.navn != opprinneligEnhet.navn) {
                val enhetMedNyttNavn = opprinneligEnhet.copy(navn = oppdatertEnhet.navn)
                transactionTemplate.executeWithoutResult {
                    navEnhetRepository.update(enhetMedNyttNavn)
                    kafkaProducerService.publiserNavEnhet(enhetMedNyttNavn)
                }
                log.info(
                    "Oppdaterer navn for enhetId=${opprinneligEnhet.enhetId} fra '${opprinneligEnhet.navn}' til '${oppdatertEnhet.navn}'",
                )
            } else if (oppdatertEnhet == null) {
                log.error("Fant ikke enhet for enhetId=${opprinneligEnhet.enhetId} i Norg")
            }
        }
    }
}
