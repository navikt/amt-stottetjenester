package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Skriver relasjoner til normaliserte tabeller innenfor samme transaksjon som personalia.
 *
 * Tjenesten mottar ferdig beregnede operasjoner og utfører dem mot `ansatt_arrangor_rolle`,
 * `ansatt_arrangor_veileder` og `ansatt_arrangor_koordinator`. Operasjonene er bevisst
 * finkornede — legg til eller deaktiver enkeltrader — i stedet for å skrive et helt snapshot.
 *
 * [AnsattArrangorRepository.replaceForAnsatt] brukes derfor bare ved opprettelse av en ny
 * ansatt. Eksisterende ansatte har fått grunnlaget sitt gjennom Flyway-backfillen, og en
 * full erstatning ved hver endring ville skrevet om historikk som allerede er korrekt.
 *
 * Når én forretningsoperasjon påvirker flere rader, beregnes tidspunktet én gang og sendes
 * videre, slik at rader som hører til samme hendelse får identisk `gyldig_fra`/`gyldig_til`.
 *
 * Kafka-hendelser med personalia går utenom denne tjenesten og bruker
 * [AnsattRepository.updatePersonalia], som ikke rører arrangørrelasjonene. Det hindrer at et
 * foreldet [AnsattDbo]-snapshot overskriver nyere relasjoner.
 */
@Service
@Transactional
class AnsattArrangorSyncService(
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
) {
    /** Full initialisering brukes bare når en ny ansatt opprettes. */
    fun opprettAnsatt(ansatt: AnsattDbo): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.replaceForAnsatt(lagretAnsatt.id, ansatt.arrangorer)
        return hentLagret(lagretAnsatt.id)
    }

    fun oppdaterRoller(oppdatering: AnsattRolleoppdatering): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(oppdatering.data)
        oppdatering.nyeRoller.forEach {
            ansattArrangorRepository.insertRolle(lagretAnsatt.id, it.arrangorId, it.rolle)
        }

        oppdatering.deaktiverteTilganger.forEach { deaktiverte ->
            ansattArrangorRepository.deaktiverRolle(
                ansattId = lagretAnsatt.id,
                arrangorId = deaktiverte.arrangorId,
                rolle = deaktiverte.rolle,
            )

            deaktiverte.veiledere.forEach {
                ansattArrangorRepository.deaktiverVeileder(
                    ansattId = lagretAnsatt.id,
                    arrangorId = deaktiverte.arrangorId,
                    veileder = it,
                )
            }

            deaktiverte.koordinatorer.forEach {
                ansattArrangorRepository.deaktiverKoordinator(
                    ansattId = lagretAnsatt.id,
                    arrangorId = deaktiverte.arrangorId,
                    koordinator = it,
                )
            }
        }
        return hentLagret(lagretAnsatt.id)
    }

    fun insertVeileder(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        veileder: VeilederDeltakerDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.insertVeileder(
            ansattId = lagretAnsatt.id,
            arrangorId = arrangorId,
            veileder = veileder,
        )

        return hentLagret(lagretAnsatt.id)
    }

    fun deaktiverVeiledere(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        veiledere: List<VeilederDeltakerDbo>,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        veiledere.forEach {
            ansattArrangorRepository.deaktiverVeileder(
                ansattId = lagretAnsatt.id,
                arrangorId = arrangorId,
                veileder = it,
            )
        }
        return hentLagret(lagretAnsatt.id)
    }

    fun insertKoordinator(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.insertKoordinator(
            ansattId = lagretAnsatt.id,
            arrangorId = arrangorId,
            koordinator = koordinator,
        )
        return hentLagret(lagretAnsatt.id)
    }

    fun deaktiverKoordinator(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.deaktiverKoordinator(
            ansattId = lagretAnsatt.id,
            arrangorId = arrangorId,
            koordinator = koordinator,
        )
        return hentLagret(lagretAnsatt.id)
    }

    fun deaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        deaktiveringsdato: ZonedDateTime,
    ): List<UUID> = ansattArrangorRepository.deaktiverVeiledereForDeltaker(
        deltakerId = deltakerId,
        deaktiveringsdato = deaktiveringsdato,
    )

    fun maybeReaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        terskel: ZonedDateTime,
    ): List<UUID> = ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(
        deltakerId = deltakerId,
        deaktiveringsdato = terskel,
    )

    private fun hentLagret(ansattId: UUID): AnsattDbo = checkNotNull(
        ansattRepository.get(ansattId),
    ) {
        "Ansatt $ansattId forsvant i samme transaksjon"
    }
}
