package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.kafka.consumer.dto.SisteTildeltVeilederPayload
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class TildeltVeilederConsumer(
    private val pdlClient: PdlClient,
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(value: String) {
        val sisteTildeltVeileder = objectMapper.readValue<SisteTildeltVeilederPayload>(value)

        val gjeldendeIdent = pdlClient
            .hentIdenter(sisteTildeltVeileder.aktorId)
            .finnGjeldendeIdent()
            .getOrThrow()

        val brukerId = navBrukerRepository.finnBrukerId(gjeldendeIdent.ident)

        if (brukerId == null) {
            log.info("Tildelt veileder endret. Nav-bruker finnes ikke, hopper over Kafka-melding")
            return
        }

        val veileder = navAnsattService.hentEllerOpprettAnsatt(sisteTildeltVeileder.veilederId)

        navBrukerService.oppdaterNavVeileder(brukerId, veileder)
        log.info("Tildelt veileder endret. Veileder ${veileder.id} tildelt til Nav-bruker $brukerId")
    }
}
