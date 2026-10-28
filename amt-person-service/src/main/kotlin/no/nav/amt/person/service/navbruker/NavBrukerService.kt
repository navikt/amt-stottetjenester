package no.nav.amt.person.service.navbruker

import no.nav.amt.person.service.clients.krr.Kontaktinformasjon
import no.nav.amt.person.service.clients.krr.KrrProxyClient
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.clients.pdl.PdlPerson
import no.nav.amt.person.service.clients.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.person.service.clients.veilarbvedtaksstotte.VeilarbvedtaksstotteClient
import no.nav.amt.person.service.kafka.producer.KafkaProducerService
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.person.PersonService
import no.nav.amt.person.service.person.PersonUpdateEvent
import no.nav.amt.person.service.person.RolleRepository
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.dbo.PersonDbo.Companion.UNKNOWN_NAME
import no.nav.amt.person.service.person.model.Adresse
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.EnvUtils
import no.nav.poao_tilgang.client.PoaoTilgangClient
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Service
class NavBrukerService(
    private val navBrukerRepository: NavBrukerRepository,
    private val personService: PersonService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val rolleRepository: RolleRepository,
    private val krrProxyClient: KrrProxyClient,
    private val poaoTilgangClient: PoaoTilgangClient,
    private val pdlClient: PdlClient,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
    private val veilarbvedtaksstotteClient: VeilarbvedtaksstotteClient,
    private val kafkaProducerService: KafkaProducerService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentEllerOpprettNavBruker(personident: String): NavBrukerDbo {
        val navBruker = navBrukerRepository.get(personident)?.let { navBrukerDbo ->
            val oppdatertNavBruker = if (skalOppdaterePersonFraPdl(navBrukerDbo.person)) {
                val oppdatertPerson = personService.hentEllerOpprettPerson(
                    personident = personident,
                    forceFetchFromPdl = true,
                )
                navBrukerRepository.getByPersonId(oppdatertPerson.id)
                    ?: throw IllegalStateException("Fant ikke Nav-bruker etter oppdatering av personident $personident")
            } else {
                navBrukerDbo
            }

            if (oppdatertNavBruker.innsatsgruppe == null) {
                oppdaterOppfolgingsperiodeOgInnsatsgruppe(oppdatertNavBruker)
                navBrukerRepository.get(oppdatertNavBruker.id)
            } else {
                oppdatertNavBruker
            }
        } ?: opprettNavBruker(personident)

        return navBruker
    }

    private fun opprettNavBruker(personident: String): NavBrukerDbo {
        val pdlPerson = pdlClient.hentPerson(personident)

        val person = personService.hentEllerOpprettPerson(personident, pdlPerson)
        val veileder = navAnsattService.hentBrukersVeileder(personident)
        val navEnhet = navEnhetService.hentNavEnhetForBruker(personident)
        val kontaktinformasjon = krrProxyClient.hentKontaktinformasjon(personident).getOrElse {
            null.also { log.warn("Navbruker ${person.id} mangler kontaktinformasjon fra KRR") }
        }
        val erSkjermet = poaoTilgangClient.erSkjermetPerson(personident).getOrThrow()
        val oppfolgingsperioder = veilarboppfolgingClient.hentOppfolgingperioder(personident)
        val innsatsgruppe = veilarbvedtaksstotteClient.hentInnsatsgruppe(personident)

        val navBruker = NavBrukerDbo(
            id = UUID.randomUUID(),
            person = person,
            navVeileder = veileder,
            navEnhet = navEnhet,
            telefon = kontaktinformasjon?.telefonnummer ?: pdlPerson.telefonnummer,
            epost = kontaktinformasjon?.epost,
            erSkjermet = erSkjermet,
            adresse = getAdresse(pdlPerson),
            sisteKrrSync = LocalDateTime.now(),
            adressebeskyttelse = pdlPerson.getAdressebeskyttelse(),
            oppfolgingsperioder = oppfolgingsperioder,
            innsatsgruppe = innsatsgruppe,
        )

        upsert(navBruker)

        if (navBruker.person.erFalskIdentitet) {
            log.warn("Opprettet ny Nav-bruker med id: ${navBruker.id} og erFalskIdentitet = true")
        } else {
            log.info("Opprettet ny Nav-bruker med id: ${navBruker.id}")
        }

        return navBrukerRepository.getByPersonId(person.id)
            ?: throw IllegalStateException(
                "Fant ikke Nav-bruker for person ${person.id}, skulle ha opprettet bruker ${navBruker.id}",
            )
    }

    private fun skalOppdaterePersonFraPdl(person: PersonDbo): Boolean = person.fornavn == UNKNOWN_NAME &&
        person.etternavn == UNKNOWN_NAME &&
        !person.erFalskIdentitet &&
        person.modifiedAt.isBefore(LocalDateTime.now().minusDays(1))

    fun upsert(navBruker: NavBrukerDbo) {
        transactionTemplate.executeWithoutResult {
            navBrukerRepository.upsert(navBruker)
            rolleRepository.insert(navBruker.person.id, Rolle.NAV_BRUKER)
            kafkaProducerService.publiserNavBruker(navBrukerRepository.get(navBruker.id))
        }
    }

    fun oppdaterNavVeileder(
        navBrukerId: UUID,
        veileder: NavAnsattDbo,
    ) {
        val bruker = navBrukerRepository.get(navBrukerId)
        if (bruker.navVeileder?.id != veileder.id) {
            upsert(bruker.copy(navVeileder = veileder))
        }
    }

    fun oppdaterOppfolgingsperiodeOgInnsatsgruppe(
        navBrukerId: UUID,
        oppfolgingsperiode: Oppfolgingsperiode,
    ) {
        val bruker = navBrukerRepository.get(navBrukerId)
        val oppfolgingsperioder = bruker.oppfolgingsperioder
            .filterNot { it.id == oppfolgingsperiode.id }
            .plus(oppfolgingsperiode)

        if (oppfolgingsperioder.toSet() != bruker.oppfolgingsperioder.toSet()) {
            val oppdatertInnsatsgruppe = veilarbvedtaksstotteClient.hentInnsatsgruppe(bruker.person.personident)

            upsert(
                bruker.copy(
                    oppfolgingsperioder = oppfolgingsperioder,
                    innsatsgruppe = oppdatertInnsatsgruppe,
                ),
            )
        }
    }

    fun oppdaterInnsatsgruppe(
        navBrukerId: UUID,
        innsatsgruppe: InnsatsgruppeV1,
    ) {
        val bruker = navBrukerRepository.get(navBrukerId)

        if (innsatsgruppe != bruker.innsatsgruppe) {
            if (harAktivOppfolgingsperiode(bruker.oppfolgingsperioder)) {
                upsert(bruker.copy(innsatsgruppe = innsatsgruppe))
            } else if (bruker.innsatsgruppe != null) {
                upsert(bruker.copy(innsatsgruppe = null))
            }
        }
    }

    fun oppdaterOppfolgingsperiodeOgInnsatsgruppe(navBruker: NavBrukerDbo) {
        val oppfolgingsperioder = veilarboppfolgingClient.hentOppfolgingperioder(navBruker.person.personident)
        val innsatsgruppe = veilarbvedtaksstotteClient.hentInnsatsgruppe(navBruker.person.personident)

        if (navBruker.innsatsgruppe != innsatsgruppe || navBruker.oppfolgingsperioder != oppfolgingsperioder) {
            upsert(
                navBruker.copy(
                    oppfolgingsperioder = oppfolgingsperioder,
                    innsatsgruppe = innsatsgruppe,
                ),
            )
            log.info("Oppdatert innsatsgruppe og oppfølgingsperidoe for Nav-bruker med id ${navBruker.id}")
        }
    }

    fun oppdaterKontaktinformasjon(bruker: NavBrukerDbo) {
        val kontaktinformasjon = krrProxyClient.hentKontaktinformasjon(bruker.person.personident).getOrElse {
            val feilmelding =
                "Klarte ikke hente kontaktinformasjon fra KRR-Proxy for bruker ${bruker.id}: ${it.message}"

            if (EnvUtils.isDev()) {
                log.info(feilmelding)
            } else {
                log.error(feilmelding)
            }
            return
        }

        val telefon = kontaktinformasjon.telefonnummer ?: pdlClient.hentTelefon(bruker.person.personident)
        oppdaterKontaktinfo(bruker, kontaktinformasjon.copy(telefonnummer = telefon))
    }

    fun settSkjermet(
        brukerId: UUID,
        erSkjermet: Boolean,
    ) {
        val bruker = navBrukerRepository.get(brukerId)
        if (bruker.erSkjermet != erSkjermet) {
            upsert(bruker.copy(erSkjermet = erSkjermet))
        }
    }

    fun syncKontaktinfoBulk(personident: Set<String>) {
        val personerChunks = personident.chunked(250) // maksgrense på 500 hos krr, vi spør på færre for å redusere timeout-problematikk

        personerChunks.forEach { personChunk ->
            val krrKontaktinfo = krrProxyClient.hentKontaktinformasjon(personChunk.toSet()).getOrElse {
                val feilmelding = "Klarte ikke hente kontaktinformasjon fra KRR-Proxy: ${it.message}"

                if (EnvUtils.isDev()) {
                    log.info(feilmelding)
                } else {
                    log.error(feilmelding)
                }
                return
            }

            for ((navBrukerId, kontaktinfo) in krrKontaktinfo) {
                val telefon = kontaktinfo.telefonnummer ?: pdlClient.hentTelefon(navBrukerId)
                val bruker = navBrukerRepository.get(navBrukerId) ?: continue

                oppdaterKontaktinfo(
                    bruker = bruker,
                    kontaktinformasjon = kontaktinfo.copy(telefonnummer = telefon),
                )
            }
        }
        log.info("Syncet kontaktinfo for ${personerChunks.size} personer")
    }

    fun fetchOppdatertKontaktinfo(personidenter: Set<String>): Map<String, Kontaktinformasjon> {
        if (personidenter.size > 500) {
            throw IllegalArgumentException("Kontaktinformasjon kan henters for maks 500 personidenter i en batch")
        }
        return krrProxyClient
            .hentKontaktinformasjon(personidenter)
            .getOrThrow()
            .mapValues { (ident, info) ->
                val bruker = navBrukerRepository.get(ident)
                    ?: throw NoSuchElementException("Kunne ikke oppdatere kontakinformasjon, bruker finnes ikke")

                val tlf = info.telefonnummer ?: pdlClient.hentTelefon(ident)

                info.copy(telefonnummer = tlf).also {
                    oppdaterKontaktinfo(bruker, it)
                }
            }
    }

    fun oppdaterAdressebeskyttelse(personident: String) {
        val navBruker = navBrukerRepository.get(personident) ?: return

        if (navBruker.person.erFalskIdentitet) {
            log.warn(
                "oppdaterAdressebeskyttelse for Nav-bruker ${navBruker.id} person-id ${navBruker.person.id} med erFalskIdentitet = true",
            )
        }

        val personOpplysninger =
            try {
                pdlClient.hentPerson(personident)
            } catch (e: Exception) {
                val feilmelding = "Klarte ikke hente person fra PDL ved oppdatert adressebeskyttelse: ${e.message}"

                if (EnvUtils.isDev()) {
                    log.info(feilmelding)
                } else {
                    log.error(feilmelding)
                }

                return
            }

        val oppdatertAdressebeskyttelse = personOpplysninger.getAdressebeskyttelse()

        if (navBruker.adressebeskyttelse == oppdatertAdressebeskyttelse) return

        upsert(
            navBruker.copy(
                adressebeskyttelse = oppdatertAdressebeskyttelse,
                adresse = getAdresse(personOpplysninger),
            ),
        )
    }

    fun oppdaterAdresse(personidenter: Set<String>) {
        personidenter.forEach { oppdaterAdresse(it) }
    }

    private fun oppdaterAdresse(personident: String) {
        val navBruker = navBrukerRepository.get(personident) ?: return

        if (navBruker.person.erFalskIdentitet) {
            log.warn("oppdaterAdresse for Nav-bruker ${navBruker.id} person-id ${navBruker.person.id} med erFalskIdentitet = true")
        }

        val personOpplysninger =
            try {
                pdlClient.hentPerson(personident)
            } catch (e: Exception) {
                val feilmelding = "Klarte ikke hente person fra PDL ved oppdatert adresse: ${e.message}"

                if (EnvUtils.isDev()) {
                    log.info(feilmelding)
                } else {
                    log.error(feilmelding)
                }

                return
            }

        val oppdatertAdresse = getAdresse(personOpplysninger)

        if (navBruker.adresse == oppdatertAdresse) return

        upsert(navBruker.copy(adresse = oppdatertAdresse))
        log.info("Oppdatert adresse for Nav-bruker med personId ${navBruker.person.id}")
    }

    private fun getAdresse(personopplysninger: PdlPerson): Adresse? {
        val adressebeskyttelse = personopplysninger.getAdressebeskyttelse()
        return if (adressebeskyttelse == null) {
            personopplysninger.adresse
        } else {
            null
        }
    }

    @EventListener
    fun onPersonUpdate(personUpdateEvent: PersonUpdateEvent) {
        navBrukerRepository.get(personUpdateEvent.person.personident)?.let {
            kafkaProducerService.publiserNavBruker(it)
        }
    }

    private fun oppdaterKontaktinfo(
        bruker: NavBrukerDbo,
        kontaktinformasjon: Kontaktinformasjon,
    ) {
        if (bruker.telefon == kontaktinformasjon.telefonnummer && bruker.epost == kontaktinformasjon.epost) {
            navBrukerRepository.upsert(bruker.copy(sisteKrrSync = LocalDateTime.now()))
            log.info("Ingen endring i kontaktinfo for personId ${bruker.person.id}")
            return
        }

        if (bruker.telefon != null && kontaktinformasjon.telefonnummer == null) {
            log.info("Fjerner telefonnummer for personId ${bruker.person.id}")
        }
        if (bruker.epost != null && kontaktinformasjon.epost == null) {
            log.info("Fjerner epostadresse for personId ${bruker.person.id}")
        }
        upsert(
            bruker.copy(
                telefon = kontaktinformasjon.telefonnummer,
                epost = kontaktinformasjon.epost,
                sisteKrrSync = LocalDateTime.now(),
            ),
        )
    }
}
