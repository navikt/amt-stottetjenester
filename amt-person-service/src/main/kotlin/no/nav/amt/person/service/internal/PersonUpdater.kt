package no.nav.amt.person.service.internal

import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Personident.Companion.finnGjeldendeIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
}
