package no.nav.amt.altinn.acl.service

import no.nav.amt.altinn.acl.client.altinn.Altinn3Client
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.domain.RollerIOrganisasjon
import no.nav.amt.altinn.acl.repository.PersonRepository
import no.nav.amt.altinn.acl.repository.RolleRepository
import no.nav.amt.altinn.acl.repository.dbo.RolleDbo
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

@Service
class RolleService(
    private val personRepository: PersonRepository,
    private val rolleRepository: RolleRepository,
    private val altinnClient: Altinn3Client,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getRollerForPerson(norskIdent: String): List<RollerIOrganisasjon> {
        val person = personRepository.get(norskIdent)

        return when {
            person == null -> {
                val roller = getAndSaveRollerFromAltinn(norskIdent)
                roller.mapToRollerIOrganisasjon()
            }

            person.lastSynchronized.isBefore(ZonedDateTime.now().minusHours(1)) -> {
                updateRollerFromAltinn(
                    personId = person.id,
                    norskIdent = norskIdent,
                )
                rolleRepository.hentGyldigeRollerForPerson(norskIdent).mapToRollerIOrganisasjon()
            }

            else -> {
                val roller = rolleRepository.hentGyldigeRollerForPerson(norskIdent)

                if (roller.isEmpty()) {
                    updateRollerFromAltinn(
                        personId = person.id,
                        norskIdent = norskIdent,
                    )
                    rolleRepository.hentGyldigeRollerForPerson(norskIdent).mapToRollerIOrganisasjon()
                } else {
                    roller.mapToRollerIOrganisasjon()
                }
            }
        }
    }

    fun synchronizeUsers(
        max: Int = 25,
        synchronizedBefore: OffsetDateTime = OffsetDateTime.now().minusWeeks(1),
    ) {
        val personsToSynchronize = personRepository.getUnsynchronizedPersons(
            maxSize = max,
            synchronizedBefore = synchronizedBefore,
        )

        log.info("Starter synkronisering av ${personsToSynchronize.size} brukere med utgått tilgang")

        personsToSynchronize.forEach { personDbo ->
            updateRollerFromAltinn(
                personId = personDbo.id,
                norskIdent = personDbo.norskIdent,
            )
        }

        log.info("Fullført synkronisering av ${personsToSynchronize.size} brukere med utgått tilgang")
    }

    private fun getAndSaveRollerFromAltinn(norskIdent: String): List<RolleDbo> {
        val start = Instant.now()

        val rolleMapFraAltinn: Map<RolleType, List<String>> = try {
            altinnClient.hentRoller(norskIdent, RolleType.entries).filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            log.warn(
                "Klarte ikke hente roller for ny bruker, exceptionType={}, statusCode={}, errorCode={}, traceId={}",
                e.javaClass.name,
                e.safeStatusCode(),
                e.safeErrorCode(),
                MDC.get("trace_id"),
            )
            // Feilen fanges her, så API-et svarer 200 med tom rolleliste; personen lagres ikke.
            return emptyList()
        }

        if (rolleMapFraAltinn.isEmpty()) {
            log.info("Bruker har ingen tilganger i Altinn")
            return emptyList()
        }

        val rollerOgOrganisasjonsnumreForLagring = rolleMapFraAltinn
            .flatMap { (rolleFraAltinn, organisasjonsnumre) ->
                organisasjonsnumre.map { Pair(rolleFraAltinn, it) }
            }.toSet()

        transactionTemplate.executeWithoutResult {
            val person = personRepository.createAndSetSynchronized(norskIdent)

            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = rollerOgOrganisasjonsnumreForLagring,
            )

            val duration = Duration.between(start, Instant.now())
            log.info("Saved roller for person with id ${person.id} in ${duration.toMillis()} ms")
        }

        return rolleRepository.hentGyldigeRollerForPerson(norskIdent)
    }

    fun updateRollerFromAltinn(
        personId: Long,
        norskIdent: String,
    ) {
        val start = Instant.now()
        val synchronizationAttempt = personRepository.reserveSynchronizationAttempt(personId)

        val rolleMapForPersonFraAltinn: Map<RolleType, List<String>> = try {
            altinnClient.hentRoller(
                norskIdent = norskIdent,
                roller = RolleType.entries,
            )
        } catch (e: Exception) {
            log.warn(
                "Klarte ikke oppdatere roller for brukerId={}, bruker lagrede roller om eksisterer, " +
                    "exceptionType={}, statusCode={}, errorCode={}, traceId={}",
                personId,
                e.javaClass.name,
                e.safeStatusCode(),
                e.safeErrorCode(),
                MDC.get("trace_id"),
            )
            return
        }

        val rollerFraAltinn = rolleMapForPersonFraAltinn
            .flatMap { (rolle, organisasjonsnumre) -> organisasjonsnumre.map { rolle to it } }
            .toSet()

        val synchronized = transactionTemplate.execute {
            val person = personRepository.lockForUpdate(personId)
            if (person.appliedSynchronizationAttempt >= synchronizationAttempt) {
                log.info(
                    "Ignorerer utdatert synkroniseringsforsøk {} for person id {}",
                    synchronizationAttempt,
                    personId,
                )
                return@execute false
            }

            val alleEksisterendeRollerForPersonFraDb =
                rolleRepository.hentGyldigeRollerForPerson(norskIdent)

            val eksisterendeRoller = alleEksisterendeRollerForPersonFraDb
                .map { it.rolleType to it.organisasjonsnummer }
                .toSet()

            val rolleIderSomSkalFjernes = alleEksisterendeRollerForPersonFraDb
                .filter { it.rolleType to it.organisasjonsnummer !in rollerFraAltinn }
                .map { it.id }
                .toSet()

            val nyeRoller = rollerFraAltinn - eksisterendeRoller

            if (rolleIderSomSkalFjernes.isEmpty() && nyeRoller.isEmpty()) {
                log.info("Ingen endring i roller for person id $personId")
            }

            if (rolleIderSomSkalFjernes.isNotEmpty()) {
                // invalider roller person ikke lenger har
                rolleRepository.fjernRoller(rolleIderSomSkalFjernes)
                log.debug(
                    "User {} lost roles {}",
                    personId,
                    rolleIderSomSkalFjernes,
                )
            }

            if (nyeRoller.isNotEmpty()) {
                rolleRepository.createRoller(
                    personId = personId,
                    rolleOgOrganisasjonsnummerSett = nyeRoller,
                )
                log.debug(
                    "User {} got {}",
                    personId,
                    nyeRoller,
                )
            }

            personRepository.completeSynchronization(
                personId = personId,
                norskIdent = norskIdent,
                synchronizationAttempt = synchronizationAttempt,
            )
            true
        }

        if (!synchronized) return

        val duration = Duration.between(start, Instant.now())
        log.info("Updated roller for person with id $personId in ${duration.toMillis()} ms")
    }
}
