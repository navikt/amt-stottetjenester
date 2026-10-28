package no.nav.amt.person.service.person

import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Rolle
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class ArrangorAnsattService(
    private val personService: PersonService,
    private val rolleRepository: RolleRepository,
    private val kafkaProducerService: KafkaProducerService,
) {
    /**
     * Henter eller oppretter personen og gir den rollen ARRANGOR_ANSATT.
     *
     * Det sendes bevisst ingen Kafka-melding når rollen tildeles. amt-arrangor kaller
     * dette endepunktet (POST /api/arrangor-ansatt) og får personalia i svaret. En melding
     * med de samme dataene rett etterpå ville vært overflødig.
     *
     * Topicen amt.arrangor-ansatt-personalia-v1 brukes bare til senere endringer i personalia.
     * De sendes fra [onPersonUpdate].
     *
     * Metoden er ikke @Transactional, slik at PDL-oppslaget ikke holder en databasetransaksjon
     * åpen. Rolle-insert er idempotent, så et nytt kall retter opp en person som mangler rollen.
     */
    fun hentEllerOpprettAnsatt(personident: String): PersonDbo {
        val person = personService.hentEllerOpprettPerson(personident)
        rolleRepository.insert(person.id, Rolle.ARRANGOR_ANSATT)
        return person
    }

    /**
     * Publiserer endrede personalia for personer som allerede har rollen ARRANGOR_ANSATT.
     *
     * For en ny person kjører denne før rollen er lagt til i [hentEllerOpprettAnsatt], og
     * sender derfor ingenting. amt-arrangor oppdaterer bare ansatte som allerede finnes, og
     * hopper over ukjente.
     */
    @EventListener
    fun onPersonUpdate(personUpdateEvent: PersonUpdateEvent) {
        val person = personUpdateEvent.person
        if (rolleRepository.harRolle(person.id, Rolle.ARRANGOR_ANSATT)) {
            kafkaProducerService.publiserArrangorAnsatt(person)
        }
    }
}
