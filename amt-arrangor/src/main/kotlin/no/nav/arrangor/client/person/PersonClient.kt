package no.nav.arrangor.client.person

import no.nav.amt.kontrakter.person.ArrangorAnsattDto
import no.nav.amt.kontrakter.person.ArrangorAnsattRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PersonClient(
    private val personApi: PersonApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentPersonalia(personident: String): Result<ArrangorAnsattDto> = runCatching {
        personApi
            .hentPersonalia(ArrangorAnsattRequest(personident))
            .also { log.debug("Hentet personalia for person") }
    }
}
