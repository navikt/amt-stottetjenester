package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.kafka.consumer.dto.SisteOppfolgingsperiodePayload
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navenhet.NavEnhetService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class SisteOppfolgingsperiodeConsumer(
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val navEnhetService: NavEnhetService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(value: String) {
        val sisteOppfolgingsperiodePayload = objectMapper.readValue<SisteOppfolgingsperiodePayload>(value)
        val navBruker = navBrukerRepository.get(sisteOppfolgingsperiodePayload.ident) ?: return

        log.info("Endrer oppfolgingsenhet på NavBruker med id=${navBruker.id}")

        /*
         * Kontor nulles ut når en person går ut av oppfølging men dette ønskes ikke at vi viderefører.
         * Tidligere hentet vi oppfølgingsenhet fra Arena og der var det ikke mulig å slette oppfølgingsenhet.
         * Vurderingen, når det nå er mulig å slette, er at vi likevel beholder den.
         * Oppfølgingsenhet deles på deltaker-v1, deltaker-ekstern-v1 og finnes i arrangør-flaten.
         */
        val navEnhet = sisteOppfolgingsperiodePayload.kontor?.let {
            navEnhetService.hentEllerOpprettNavEnhet(sisteOppfolgingsperiodePayload.kontor.kontorId)
        } ?: navBruker.navEnhet

        navBrukerService.oppdaterOppfolgingsperiodeOgInnsatsgruppe(
            navBruker = navBruker.copy(navEnhet = navEnhet),
            oppfolgingsperiode = sisteOppfolgingsperiodePayload.toOppfolgingsperiode(),
        )
    }
}
