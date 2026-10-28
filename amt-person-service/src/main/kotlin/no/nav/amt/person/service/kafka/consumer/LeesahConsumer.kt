package no.nav.amt.person.service.kafka.consumer

import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonService
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.adressebeskyttelse.Adressebeskyttelse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

enum class OpplysningsType {
    NAVN_V1,
    ADRESSEBESKYTTELSE_V1,
    KONTAKTADRESSE_V1,
    BOSTEDSADRESSE_V1,
    OPPHOLDSADRESSE_V1,
    FALSK_ID_V1,
}

@Component
class LeesahConsumer(
    private val personService: PersonService,
    private val navBrukerService: NavBrukerService,
    private val personRepository: PersonRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(personhendelse: Personhendelse) {
        when (personhendelse.opplysningstype) {
            OpplysningsType.NAVN_V1.toString() -> handterNavn(personhendelse.personidenter.toSet())

            OpplysningsType.ADRESSEBESKYTTELSE_V1.toString() ->
                handterAdressebeskyttelse(
                    personidenter = personhendelse.personidenter.toSet(),
                    adressebeskyttelse = personhendelse.adressebeskyttelse,
                )

            OpplysningsType.BOSTEDSADRESSE_V1.toString() -> handterAdresse(personhendelse.personidenter.toSet())

            OpplysningsType.KONTAKTADRESSE_V1.toString() -> handterAdresse(personhendelse.personidenter.toSet())

            OpplysningsType.OPPHOLDSADRESSE_V1.toString() -> handterAdresse(personhendelse.personidenter.toSet())

            OpplysningsType.FALSK_ID_V1.toString() -> handterFalskIdentitet(personhendelse.personidenter.toSet())
        }
    }

    private fun handterAdressebeskyttelse(
        personidenter: Set<String>,
        adressebeskyttelse: Adressebeskyttelse?,
    ) {
        if (adressebeskyttelse == null) {
            log.warn("Mottok melding med opplysningstype Adressebeskyttelse fra pdl-leesah men adressebeskyttelse manglet")
            return
        }

        val lagredePersonidenter = personRepository.getPersoner(personidenter).map { it.personident }

        lagredePersonidenter.forEach {
            navBrukerService.oppdaterAdressebeskyttelse(it)
        }
    }

    private fun handterNavn(personidenter: Set<String>) = personRepository
        .getPersoner(personidenter)
        .forEach { personDbo -> personService.oppdaterNavn(personDbo) }

    private fun handterAdresse(personidenter: Set<String>) {
        val lagredePersonidenter = personRepository
            .getPersoner(personidenter)
            .map { it.personident }
            .toSet()

        if (lagredePersonidenter.isEmpty()) return

        navBrukerService.oppdaterAdresse(lagredePersonidenter)
    }

    private fun handterFalskIdentitet(personidenter: Set<String>) {
        log.info("Mottatt melding om FALSK_ID_V1 på Leesah topic")

        personRepository.getPersoner(personidenter).forEach {
            personService.hentEllerOpprettPerson(
                personident = it.personident,
                forceFetchFromPdl = true,
            )
        }
    }
}
