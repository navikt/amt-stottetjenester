package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class InnsatsgruppeConsumer(
    private val pdlClient: PdlClient,
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(value: String) {
        val siste14aVedtak = objectMapper.readValue<Siste14aVedtak>(value)

        val gjeldendeIdent = pdlClient
            .hentIdenter(siste14aVedtak.aktorId)
            .finnGjeldendeIdent()
            .getOrThrow()

        val brukerId = navBrukerRepository.finnBrukerId(gjeldendeIdent.ident)

        if (brukerId == null) {
            log.info("Innsatsgruppe endret. Nav-bruker finnes ikke, hopper over Kafka-melding")
            return
        }

        navBrukerService.oppdaterInnsatsgruppe(
            navBrukerId = brukerId,
            innsatsgruppe = siste14aVedtak.innsatsgruppe,
        )
        log.info("Oppdatert innsatsgruppe for bruker $brukerId")
    }

    data class Siste14aVedtak(
        val aktorId: String,
        val innsatsgruppe: InnsatsgruppeV1,
    )
}
