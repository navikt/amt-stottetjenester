package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navbruker.NavBrukerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class SkjermetPersonConsumer(
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(
        personident: String,
        erSkjermetAsJson: String,
    ) {
        val brukerId = navBrukerRepository.finnBrukerId(personident) ?: return
        navBrukerService.settSkjermet(
            brukerId = brukerId,
            erSkjermet = objectMapper.readValue<Boolean>(erSkjermetAsJson),
        )
    }

    fun ingestTombstone(personident: String) {
        navBrukerRepository
            .finnBrukerId(personident)
            ?.let { brukerId ->
                val logMessage = "Kan ikke ingeste tombstone for eksisterende Nav-bruker $brukerId"
                throw IllegalArgumentException(logMessage).also { log.error(logMessage) }
            }
    }
}
