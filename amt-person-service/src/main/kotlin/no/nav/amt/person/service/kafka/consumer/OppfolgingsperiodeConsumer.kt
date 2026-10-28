package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.kafka.consumer.dto.SisteOppfolgingsperiodeKafkaPayload
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class OppfolgingsperiodeConsumer(
    private val pdlClient: PdlClient,
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(value: String) {
        val sisteOppfolgingsperiode = objectMapper.readValue<SisteOppfolgingsperiodeKafkaPayload>(value)

        val gjeldendeIdent = try {
            pdlClient
                .hentIdenter(sisteOppfolgingsperiode.aktorId)
                .finnGjeldendeIdent()
                .getOrThrow()
        } catch (e: Exception) {
            if (e.message?.contains("Fant ikke person") == true) {
                log.warn(e.message, e)
                return
            }
            throw e
        }

        val brukerId = navBrukerRepository.finnBrukerId(gjeldendeIdent.ident)

        if (brukerId == null) {
            log.info("Nav-bruker finnes ikke i tabellen nav_bruker, dropper videre prosessering")
            return
        }

        navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(
            navBrukerId = brukerId,
            oppfolgingsperiode = sisteOppfolgingsperiode.toOppfolgingsperiode(),
        )

        log.info("Oppdatert oppfølgingsperiode med id ${sisteOppfolgingsperiode.uuid} for bruker $brukerId")
    }
}
