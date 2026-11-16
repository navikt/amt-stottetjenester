package no.nav.amt.person.service.internal

import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.ArrayDeque

@Service
class PersonUpdater(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val pdlClient: PdlClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun oppdaterPersonidenter(startOffset: Int = 0) {
        var offset = startOffset
        var personer: List<PersonDbo>

        do {
            personer = personRepository.getAll(offset)

            for ((id, personident) in personer) {
                val identer = pdlClient.hentIdenter(personident)
                if (identer.isEmpty()) continue

                personService.oppdaterPersonIdent(identer)

                identer.finnGjeldendeIdent().onSuccess { ident ->
                    if (ident.ident != personident) {
                        log.info("Ny gjeldende ident for person $id")
                    }
                }
            }

            log.info("Oppdaterte personidenter for personer fra offset $offset til ${offset + personer.size}")
            offset += personer.size
        } while (personer.isNotEmpty())
    }

    fun oppdaterAllePersonnavn() {
        var offset = 0
        var personer: List<PersonDbo>
        val sistePdlKall = ArrayDeque<Boolean>(FEILRATE_VINDU_STORRELSE)
        var feilISistePdlKall = 0
        var totaltAntallKall = 0
        var totaltAntallFeil = 0

        do {
            personer = personRepository.getAll(offset, PERSON_BATCH_SIZE)

            personer.forEach { person ->
                val feilet = personService.oppdaterNavnForBulk(person).isFailure
                totaltAntallKall++
                if (feilet) {
                    totaltAntallFeil++
                    feilISistePdlKall++
                }

                sistePdlKall.addLast(feilet)
                if (sistePdlKall.size > FEILRATE_VINDU_STORRELSE && sistePdlKall.removeFirst()) {
                    feilISistePdlKall--
                }

                if (
                    sistePdlKall.size == FEILRATE_VINDU_STORRELSE &&
                    overskriderFeilrate(feilISistePdlKall, sistePdlKall.size)
                ) {
                    log.error(
                        "Stopper navneoppdatering: {} av de siste {} PDL-kallene feilet",
                        feilISistePdlKall,
                        sistePdlKall.size,
                    )
                    throw PdlFailureRateExceededException(feilISistePdlKall, sistePdlKall.size)
                }
            }

            log.info(
                "Behandlet personer fra offset $offset til ${offset + personer.size}; PDL-feil: $totaltAntallFeil av $totaltAntallKall",
            )
            offset += personer.size
        } while (personer.isNotEmpty())

        if (
            sistePdlKall.isNotEmpty() &&
            sistePdlKall.size < FEILRATE_VINDU_STORRELSE &&
            overskriderFeilrate(feilISistePdlKall, sistePdlKall.size)
        ) {
            log.error(
                "Navneoppdateringen er ferdig med for høy PDL-feilrate: {} av {} kall feilet",
                feilISistePdlKall,
                sistePdlKall.size,
            )
            throw PdlFailureRateExceededException(feilISistePdlKall, sistePdlKall.size)
        }

        log.info("Navneoppdateringen er ferdig: PDL-feil {} av {} kall", totaltAntallFeil, totaltAntallKall)
    }

    private fun overskriderFeilrate(
        feil: Int,
        antall: Int,
    ): Boolean = feil * 100 > antall * MAKS_FEILRATE_PROSENT

    companion object {
        private const val PERSON_BATCH_SIZE = 100
        private const val FEILRATE_VINDU_STORRELSE = 500
        private const val MAKS_FEILRATE_PROSENT = 5
    }
}

class PdlFailureRateExceededException(
    val failures: Int,
    val windowSize: Int,
) : IllegalStateException(
        "PDL-feilrate overskred 5 %: $failures av $windowSize kall feilet",
    )
