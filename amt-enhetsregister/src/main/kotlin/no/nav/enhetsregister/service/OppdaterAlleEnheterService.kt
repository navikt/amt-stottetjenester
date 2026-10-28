package no.nav.enhetsregister.service

import no.nav.enhetsregister.client.BronnoysundClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.ceil

@Service
class OppdaterAlleEnheterService(
    private val bronnoysundClient: BronnoysundClient,
    private val enhetService: EnhetService,
) {
    companion object {
        const val CHUNCK_SIZE = 10_000
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    fun oppdaterAlleModerenheter() {
        val timeStarted = Instant.now()

        log.info("Starter å laste ned alle moderenheter...")

        val alleModerenheter = bronnoysundClient.hentAlleModerenheter()
        val antallModerenheter = alleModerenheter.size

        log.info(
            "Moderenheter lastet ned. antallEnheter=$antallModerenheter tidBrukt=${Instant.now().epochSecond - timeStarted.epochSecond}s",
        )

        val totalChunks = ceil((antallModerenheter / CHUNCK_SIZE).toDouble())

        alleModerenheter.chunked(CHUNCK_SIZE).forEachIndexed { idx, chunk ->
            val upserts = chunk.map {
                EnhetService.UpsertEnhet(
                    organisasjonsnummer = it.organisasjonsnummer,
                    navn = it.navn,
                    overordnetEnhetOrgNr = null,
                )
            }

            enhetService.oppdaterEnheter(upserts)

            log.info("Skrev chunk=$idx til databasen. Progresjon: $idx/${totalChunks.toInt()}")
        }

        log.info("Alle moderenheter er skrevet til databasen. tidBrukt=${Instant.now().epochSecond - timeStarted.epochSecond}s")
    }

    fun oppdaterAlleUnderenheter() {
        val timeStarted = Instant.now()

        log.info("Starter å laste ned alle underenheter...")

        val alleUnderenheter = bronnoysundClient.hentAlleUnderenheter()
        val antallUnderenheter = alleUnderenheter.size

        log.info(
            "Underenheter lastet ned. antallEnheter=$antallUnderenheter tidBrukt=${Instant.now().epochSecond - timeStarted.epochSecond}s",
        )

        val totalChunks = ceil((antallUnderenheter / CHUNCK_SIZE).toDouble())

        alleUnderenheter.chunked(CHUNCK_SIZE).forEachIndexed { idx, chunk ->
            val upserts = chunk.map {
                EnhetService.UpsertEnhet(
                    organisasjonsnummer = it.organisasjonsnummer,
                    navn = it.navn,
                    overordnetEnhetOrgNr = it.overordnetEnhet,
                )
            }

            enhetService.oppdaterEnheter(upserts)

            log.info("Skrev chunk=$idx til databasen. Progresjon: $idx/${totalChunks.toInt()}")
        }

        log.info("Alle underenheter er skrevet til databasen. tidBrukt=${Instant.now().epochSecond - timeStarted.epochSecond}s")
    }
}
