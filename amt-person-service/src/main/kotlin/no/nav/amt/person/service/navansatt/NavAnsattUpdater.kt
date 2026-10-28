package no.nav.amt.person.service.navansatt

import no.nav.amt.person.service.clients.nom.NomClient
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.navenhet.NavEnhetService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NavAnsattUpdater(
    private val navAnsattRepository: NavAnsattRepository,
    private val navAnsattService: NavAnsattService,
    private val nomClient: NomClient,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun oppdaterAlle(batchSize: Int = 100) {
        var batchNumber = 1
        val ansattBatcher = navAnsattRepository
            .getAll()
            .also { log.info("Oppdaterer ${it.size} Nav-ansatte") }
            .chunked(batchSize)

        ansattBatcher.forEach { batch ->
            log.info("Prosesserer batch ${batchNumber++}")
            val ansatte = batch.associate { it.navIdent to AnsattSomSkalOppdateres(it, false) }
            val nomResultat = nomClient.hentNavAnsatte(ansatte.keys.toList())

            val oppdaterteAnsatte = nomResultat
                .mapNotNull { nomAnsatt ->
                    ansatte[nomAnsatt.navIdent]?.let { finnOppdatering(it, nomAnsatt) }
                }.toSet()

            log.info("Oppdaterer informasjon om ${oppdaterteAnsatte.size} Nav-ansatte, sjekket ${nomResultat.size} ansatte")

            navAnsattService.upsertMany(oppdaterteAnsatte)

            ansatte.forEach { (_, ansatt) ->
                if (!ansatt.erSjekket) {
                    log.warn("Fant ikke Nav-ansatt med id ${ansatt.lagretAnsatt.id} i NOM")
                }
            }
        }
    }

    private fun finnOppdatering(
        ansatt: AnsattSomSkalOppdateres,
        nomAnsatt: NomNavAnsatt,
    ): NavAnsattDbo? {
        ansatt.erSjekket = true
        val navEnhet = nomAnsatt.navEnhetNummer?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }

        return if (ansatt.lagretAnsatt.skalOppdateres(nomAnsatt, navEnhet)) {
            val telefon = nomAnsatt.telefonnummer
                ?: ansatt.lagretAnsatt.telefon?.also {
                    log.info(
                        "Telefonnummer for Nav-ansatt ${ansatt.lagretAnsatt.id} er nullstilt i response fra Nom, ignorerer oppdatering.",
                    )
                }

            val epost = nomAnsatt.epost
                ?: ansatt.lagretAnsatt.epost?.also {
                    log.info("E-post for Nav-ansatt ${ansatt.lagretAnsatt.id} er nullstilt i response fra Nom, ignorerer oppdatering.")
                }

            NavAnsattDbo(
                id = ansatt.lagretAnsatt.id,
                navIdent = nomAnsatt.navIdent,
                navn = nomAnsatt.navn,
                epost = epost,
                telefon = telefon,
                navEnhetId = navEnhet?.id,
            )
        } else {
            null
        }
    }

    data class AnsattSomSkalOppdateres(
        val lagretAnsatt: NavAnsattDbo,
        var erSjekket: Boolean,
    )

    companion object {
        private fun NavAnsattDbo.skalOppdateres(
            nomNavAnsatt: NomNavAnsatt,
            navEnhet: NavEnhetDbo?,
        ): Boolean = this.navn != nomNavAnsatt.navn ||
            this.epost != nomNavAnsatt.epost ||
            this.telefon != nomNavAnsatt.telefonnummer ||
            this.navEnhetId != navEnhet?.id
    }
}
