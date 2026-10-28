package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattRolleService(
    private val altinnClient: AltinnAclClient,
    private val arrangorService: ArrangorService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getRollerFraAltinn(personIdent: String): List<AltinnRolle> = altinnClient.hentRoller(personIdent).getOrThrow()

    fun mapAltinnRollerTilArrangorListeForNyAnsatt(roller: List<AltinnRolle>): List<ArrangorDbo> {
        val unikeOrgnummer = roller.map { it.organisasjonsnummer }.distinct()
        val arrangorer = unikeOrgnummer.map { arrangorService.getOrCreate(it) }

        val arrangorListe = roller.mapNotNull { altinnRolle ->
            val arrangor = arrangorer.find { altinnRolle.organisasjonsnummer == it.organisasjonsnummer }
            if (arrangor != null) {
                ArrangorDbo(
                    arrangorId = arrangor.id,
                    roller = altinnRolle.roller.map { RolleDbo(it) },
                    veileder = emptyList(),
                    koordinator = emptyList(),
                )
            } else {
                null
            }
        }
        return arrangorListe
    }

    fun getAnsattDboMedOppdaterteRoller(ansattDbo: AnsattDbo): AnsattRolleoppdatering {
        val nyeRollerFraAltinn = altinnClient.hentRoller(ansattDbo.personident).getOrThrow()

        val unikeOrgnummerFraAltinn = nyeRollerFraAltinn.map { it.organisasjonsnummer }.distinct()
        val unikeArrangorerFraAltinnMedRolle = arrangorService.getOrCreate(unikeOrgnummerFraAltinn)

        val nyeRoller = altinnToRolleOgArrangor(nyeRollerFraAltinn, unikeArrangorerFraAltinnMedRolle)

        return getAnsattDboMedOppdaterteRoller(ansattDbo, nyeRoller)
    }

    private fun getAnsattDboMedOppdaterteRoller(
        ansattDbo: AnsattDbo,
        aktiveRoller: List<RolleOgArrangor>,
    ): AnsattRolleoppdatering {
        val gamleAktiveRoller = ansattDbo.arrangorer.flatMap { arrangor ->
            arrangor.roller
                .filter { it.erGyldig() }
                .map {
                    RolleOgArrangor(
                        arrangorId = arrangor.arrangorId,
                        rolle = it.rolle,
                    )
                }
        }

        val rollerSomSkalDeaktiveres = gamleAktiveRoller.filter { gammelRolle ->
            aktiveRoller.none { nyRolle ->
                nyRolle.rolle == gammelRolle.rolle && nyRolle.arrangorId == gammelRolle.arrangorId
            }
        }

        // Ett delt tidspunkt for hele denne synk-operasjonen (kan omfatte flere roller/arrangører),
        // slik at vi ikke gjentar rotårsaken til de historiske nær-duplikatene i ansatt_arrangor_rolle
        // (se AnsattArrangorSyncService): separate ZonedDateTime.now()-kall
        // per element ga mikrosekund-forskjellige gyldigTil-verdier for samme logiske hendelse.
        val deaktiveringstidspunkt = ZonedDateTime.now()

        val oppdatering = getOppdaterteArrangorerForAnsatt(
            ansattDbo = ansattDbo,
            rollerSomSkalDeaktiveres = rollerSomSkalDeaktiveres,
            aktiveRoller = aktiveRoller,
            deaktiveringstidspunkt = deaktiveringstidspunkt,
        )

        return AnsattRolleoppdatering(
            isUpdated = rollerSomSkalDeaktiveres.isNotEmpty() || aktiveRoller.size > gamleAktiveRoller.size,
            data = ansattDbo.copy(
                arrangorer = oppdatering.arrangorer,
                lastSynchronized = LocalDateTime.now(),
            ),
            nyeRoller = oppdatering.nyeRoller,
            deaktiverteTilganger = oppdatering.deaktiverteTilganger,
        )
    }

    private fun getOppdaterteArrangorerForAnsatt(
        ansattDbo: AnsattDbo,
        rollerSomSkalDeaktiveres: List<RolleOgArrangor>,
        aktiveRoller: List<RolleOgArrangor>,
        deaktiveringstidspunkt: ZonedDateTime,
    ): OppdaterteArrangorer {
        val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
        oppdaterteArrangorer.addAll(ansattDbo.arrangorer)

        val deaktiverteTilganger = rollerSomSkalDeaktiveres.mapNotNull { rolleOgArrangor ->
            val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
            deaktiverRolle(arrangor, rolleOgArrangor, ansattDbo, deaktiveringstidspunkt)
        }

        val nyeRoller = aktiveRoller.mapNotNull { rolleOgArrangor ->
            leggTilRolle(oppdaterteArrangorer, rolleOgArrangor, ansattDbo)
        }
        return OppdaterteArrangorer(oppdaterteArrangorer, nyeRoller, deaktiverteTilganger)
    }

    private fun deaktiverRolle(
        arrangor: ArrangorDbo?,
        rolleOgArrangor: RolleOgArrangor,
        ansatt: AnsattDbo,
        deaktiveringstidspunkt: ZonedDateTime,
    ): DeaktiverteTilganger? {
        if (arrangor == null) {
            logger.warn(
                "Kan ikke deaktivere rolle hos arrangør som ikke er koblet til ansatt, arrangørid ${rolleOgArrangor.arrangorId}, ansattId ${ansatt.id}",
            )
            return null
        }

        val deaktivertRolle = arrangor.roller
            .find { it.erGyldig() && it.rolle == rolleOgArrangor.rolle }
            ?.also { it.gyldigTil = deaktiveringstidspunkt }
            ?: return null

        val deaktiverteKoordinatorer = if (rolleOgArrangor.rolle == AnsattRolle.KOORDINATOR) {
            arrangor.koordinator
                .filter { it.erGyldig() }
                .onEach { it.gyldigTil = deaktiveringstidspunkt }
        } else {
            emptyList()
        }

        val deaktiverteVeiledere = if (rolleOgArrangor.rolle == AnsattRolle.VEILEDER) {
            arrangor.veileder.filter { it.erGyldig() }.onEach { it.gyldigTil = deaktiveringstidspunkt }
        } else {
            emptyList()
        }

        logger.info("Ansatt med ${ansatt.id} mistet ${rolleOgArrangor.rolle} hos ${arrangor.arrangorId}")

        return DeaktiverteTilganger(
            arrangorId = arrangor.arrangorId,
            rolle = deaktivertRolle,
            veiledere = deaktiverteVeiledere,
            koordinatorer = deaktiverteKoordinatorer,
        )
    }

    private fun leggTilRolle(
        oppdaterteArrangorer: MutableList<ArrangorDbo>,
        rolleOgArrangor: RolleOgArrangor,
        ansatt: AnsattDbo,
    ): NyRolle? {
        val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }

        val harGyldigRolle = arrangor?.roller?.any { it.rolle == rolleOgArrangor.rolle && it.erGyldig() }

        if (harGyldigRolle == true) return null

        val nyRolle = RolleDbo(rolleOgArrangor.rolle)
        val oppdatertArrangor = arrangor
            ?.copy(roller = arrangor.roller.plus(nyRolle))
            ?: ArrangorDbo(
                arrangorId = rolleOgArrangor.arrangorId,
                roller = listOf(nyRolle),
                koordinator = emptyList(),
                veileder = emptyList(),
            )

        oppdaterteArrangorer.remove(arrangor)
        oppdaterteArrangorer.add(oppdatertArrangor)

        logger.info("Ansatt med ${ansatt.id} fikk ${rolleOgArrangor.rolle} hos ${oppdatertArrangor.arrangorId}")
        return NyRolle(oppdatertArrangor.arrangorId, nyRolle)
    }

    private fun altinnToRolleOgArrangor(
        roller: List<AltinnRolle>,
        arrangorer: List<Arrangor>,
    ): List<RolleOgArrangor> = roller.flatMap { altinnRolle ->
        kombinerRollerOgArrangor(
            altinnRolle = altinnRolle,
            arrangorer = arrangorer,
        )
    }

    private fun kombinerRollerOgArrangor(
        altinnRolle: AltinnRolle,
        arrangorer: List<Arrangor>,
    ) = altinnRolle.roller.mapNotNull { ansattRolle ->
        val arrangor = arrangorer.find { arrangor -> arrangor.organisasjonsnummer == altinnRolle.organisasjonsnummer }
            ?: return@mapNotNull null

        RolleOgArrangor(
            arrangorId = arrangor.id,
            rolle = ansattRolle,
        )
    }

    private data class RolleOgArrangor(
        val arrangorId: UUID,
        val rolle: AnsattRolle,
    )

    private data class OppdaterteArrangorer(
        val arrangorer: List<ArrangorDbo>,
        val nyeRoller: List<NyRolle>,
        val deaktiverteTilganger: List<DeaktiverteTilganger>,
    )
}

data class AnsattRolleoppdatering(
    val isUpdated: Boolean,
    val data: AnsattDbo,
    val nyeRoller: List<NyRolle>,
    val deaktiverteTilganger: List<DeaktiverteTilganger>,
)

data class NyRolle(
    val arrangorId: UUID,
    val rolle: RolleDbo,
)

data class DeaktiverteTilganger(
    val arrangorId: UUID,
    val rolle: RolleDbo,
    val veiledere: List<VeilederDeltakerDbo>,
    val koordinatorer: List<KoordinatorsDeltakerlisteDbo>,
)
