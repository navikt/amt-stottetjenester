package no.nav.amt.arena.acl.consumer

import no.nav.amt.arena.acl.clients.mulighetsrommet.Gjennomforing
import no.nav.amt.arena.acl.domain.db.toUpsertInputWithStatusHandled
import no.nav.amt.arena.acl.domain.kafka.amt.AmtDeltaker
import no.nav.amt.arena.acl.domain.kafka.amt.AmtKafkaMessageDto
import no.nav.amt.arena.acl.domain.kafka.amt.AmtOperation
import no.nav.amt.arena.acl.domain.kafka.amt.PayloadType
import no.nav.amt.arena.acl.domain.kafka.amt.erAvsluttende
import no.nav.amt.arena.acl.domain.kafka.arena.ArenaHistDeltaker
import no.nav.amt.arena.acl.domain.kafka.arena.ArenaHistDeltakerKafkaMessage
import no.nav.amt.arena.acl.exceptions.ExternalSourceSystemException
import no.nav.amt.arena.acl.exceptions.IgnoredException
import no.nav.amt.arena.acl.exceptions.OperationNotImplementedException
import no.nav.amt.arena.acl.exceptions.ValidationException
import no.nav.amt.arena.acl.repositories.ArenaDataRepository
import no.nav.amt.arena.acl.repositories.DeltakerDbo
import no.nav.amt.arena.acl.repositories.DeltakerRepository
import no.nav.amt.arena.acl.services.ArenaDataIdTranslationService
import no.nav.amt.arena.acl.services.KafkaProducerService
import no.nav.amt.arena.acl.utils.asLocalDate
import no.nav.amt.arena.acl.utils.asLocalDateTime
import no.nav.amt.arena.acl.utils.tryRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Consumer for hist deltakere
 * Når man oppretter en ny deltakelse på samme gjennomføring og samme person, så historiseres den gamle deltakelsen.
 * I disse tilfellene så ønsker vi, ved overtakelse av deltakelser på ny tiltakstype, å lese inn de deltakelsene som
 * allerede er historisert.
 */
@Component
class HistDeltakerConsumer(
	private val arenaDataRepository: ArenaDataRepository,
	private val kafkaProducerService: KafkaProducerService,
	private val arenaDeltakerConsumer: ArenaDeltakerConsumer,
	private val deltakerRepository: DeltakerRepository,
	private val arenaDataIdTranslationService: ArenaDataIdTranslationService,
) : ArenaMessageConsumer<ArenaHistDeltakerKafkaMessage> {
	private val log = LoggerFactory.getLogger(javaClass)

	override fun handleArenaMessage(message: ArenaHistDeltakerKafkaMessage) {
		val arenaDeltakerRaw = message.getData()
		val arenaHistDeltakerId = arenaDeltakerRaw.HIST_TILTAKDELTAKER_ID.toString()
		val arenaGjennomforingId = arenaDeltakerRaw.TILTAKGJENNOMFORING_ID.toString()
		val gjennomforing = arenaDeltakerConsumer.getGjennomforing(arenaGjennomforingId)

		externalDeltakerGuard(arenaDeltakerRaw)
        enkeltplassGuard(arenaDeltakerRaw, gjennomforing)

		if (message.operationType != AmtOperation.CREATED) {
			log.info("Mottatt melding for hist-deltaker arenaHistId=$arenaHistDeltakerId op=${message.operationType}, blir ikke behandlet")
			throw IgnoredException("Ignorerer hist-deltaker som har operation type ${message.operationType}")
		} else {
			val histDeltaker =
				arenaDeltakerRaw
					.tryRun { it.mapTiltakDeltaker() }
					.getOrThrow()

			val eksisterendeDeltaker = getMatchingDeltaker(arenaDeltakerRaw)

			if (eksisterendeDeltaker == null) {
				// Her havner vi når vi spiller av topicen fra start
				// (fordi disse deltakerene er allerede fjernet fra arenas deltaker tabell)
				val nyDeltaker = arenaDeltakerConsumer.createDeltaker(histDeltaker, gjennomforing, erHistDeltaker = true)

				nyDeltaker.validerGyldigHistDeltaker()
				log.info(
					"Fant ingen match for hist-deltaker $arenaHistDeltakerId, oppretter ny deltaker ${nyDeltaker.id} og lagrer mapping og sender videre",
				)
                /*
                    Her må det produseres melding til ny topic hvis vi skal lese historiserte deltakelser
                    på tiltak som vi skal bli master for
                    Tidligere ble det produsert data til amt-tiltak-v1 men denne topicen skal ikke brukes mer(siden data da går vi amt-tiltak)
                 */
				sendMessage(nyDeltaker, arenaHistDeltakerId)
			} else {
				log.info("Hist deltaker $arenaHistDeltakerId matcher deltaker ${eksisterendeDeltaker.arenaId}")

				val eksisterendeDeltakerAmtId =
					arenaDataIdTranslationService.hentAmtId(eksisterendeDeltaker.arenaId.toString())
						?: throw ValidationException(
							"Fant matchende deltaker for hist deltaker $arenaHistDeltakerId men fant ikke deltakeren igjen i translation tabellen",
						)
				arenaDataIdTranslationService.lagreHistDeltakerId(
					amtDeltakerId = eksisterendeDeltakerAmtId,
					histDeltakerArenaId = arenaHistDeltakerId,
				)
			}
			arenaDataRepository.upsert(
				message.toUpsertInputWithStatusHandled(arenaHistDeltakerId, note = "Fant match? ${eksisterendeDeltaker != null}"),
			)
		}
	}

    private fun enkeltplassGuard(arenaDeltakerRaw: ArenaHistDeltaker, gjennomforing: Gjennomforing) {
        if(gjennomforing.erEnkelplass()) {
            /*
                Det skal bare være en deltakelse per gjennomføring på enkeltplasser selv om
                en deltaker i teorien kan bli historisert i arena
             */
            throw IgnoredException("Arena hist-deltaker med id ${arenaDeltakerRaw.HIST_TILTAKDELTAKER_ID} ignoreres fordi det er en enkeltplass")
        }

    }

	private fun externalDeltakerGuard(arenaDeltakerRaw: ArenaHistDeltaker) {
		val deltakerHistId = arenaDeltakerRaw.HIST_TILTAKDELTAKER_ID.toString()
		if (!arenaDeltakerRaw.EKSTERN_ID.isNullOrEmpty()) {
			val eksternId = UUID.fromString(arenaDeltakerRaw.EKSTERN_ID)

			val arenaId = arenaDataIdTranslationService.hentArenaHistId(eksternId)
			if (arenaId == null) {
				arenaDataIdTranslationService.lagreHistDeltakerId(
					amtDeltakerId = eksternId,
					histDeltakerArenaId = deltakerHistId,
				)
			} else if (arenaId != deltakerHistId) {
				log.error(
					"Fikk arenadeltaker med id $deltakerHistId og EKSTERN_ID ${arenaDeltakerRaw.EKSTERN_ID} men arenaId er allerede mappet til $arenaId",
				)
				throw ValidationException(
					"Fikk arena hist-deltaker med id $deltakerHistId og EKSTERN_ID ${arenaDeltakerRaw.EKSTERN_ID} men arenaId er allerede mappet til $arenaId",
				)
			}

			throw ExternalSourceSystemException("hist-deltaker har eksternid ${arenaDeltakerRaw.EKSTERN_ID}")
		}
		if (arenaDeltakerRaw.DELTAKERTYPEKODE == "EKSTERN") {
			throw ExternalSourceSystemException("hist-deltaker har deltakertypekode ekstern, arenaid $deltakerHistId")
		}
	}

	private fun sendMessage(
		deltaker: AmtDeltaker,
		arenaDeltakerId: String,
	) {
		val deltakerKafkaMessage =
			AmtKafkaMessageDto(
				type = PayloadType.DELTAKER,
				operation = AmtOperation.CREATED,
				payload = deltaker,
			)
		kafkaProducerService.sendTilAmtTiltak(deltaker.id, deltakerKafkaMessage)
		log.info(
			"Melding for deltaker id=${deltaker.id} arenaId=$arenaDeltakerId transactionId=${deltakerKafkaMessage.transactionId} op=${deltakerKafkaMessage.operation} er sendt",
		)
	}

	private fun getMatchingDeltaker(arenaHistDeltaker: ArenaHistDeltaker): DeltakerDbo? {
		val personId =
			arenaHistDeltaker.PERSON_ID
				?: throw ValidationException("Kan ikke matche hist deltaker som mangler PERSON_ID")
		val datoStatusendring =
			arenaHistDeltaker.DATO_STATUSENDRING?.asLocalDateTime()
				?: throw ValidationException("Kan ikke matche hist deltaker som mangler DATO_STATUSENDRING")
		val datoFra = arenaHistDeltaker.DATO_FRA?.asLocalDate()
		val datoTil = arenaHistDeltaker.DATO_TIL?.asLocalDate()
		val arenaDeltakere =
			deltakerRepository
				.getDeltakereForPerson(personId, arenaHistDeltaker.TILTAKGJENNOMFORING_ID)

		val matchendeDeltakere =
			arenaDeltakere
				.filter { it.datoFra == datoFra }
				.filter { it.datoTil == datoTil }
				.filter { it.datoStatusEndring == datoStatusendring }

		if (arenaDeltakere.isEmpty()) {
			log.info(
				"Fant ingen match for hist deltaker med id ${arenaHistDeltaker.HIST_TILTAKDELTAKER_ID} " +
					"fordi personen har ingen andre deltakelser i databasen",
			)
			return null
		} else if (matchendeDeltakere.isEmpty()) {
			log.info(
				"Fant ingen match for hist-deltaker med id ${arenaHistDeltaker.HIST_TILTAKDELTAKER_ID}. " +
					"fradato: $datoFra, tildato: $datoTil, modDato: $datoStatusendring" +
					"Personen har ${arenaDeltakere.size} andre deltakelser",
			)

			arenaDeltakere.forEach {
				log.info("Ingen match med arenaId: ${it.arenaId}, fradato: ${it.datoFra}, tildato ${it.datoTil}, ${it.modDato}")
			}
			return null
		} else if (matchendeDeltakere.size == 1) {
			log.info("hist deltaker med ${arenaHistDeltaker.HIST_TILTAKDELTAKER_ID} matcher med ${matchendeDeltakere.first().arenaId}")
			return matchendeDeltakere.first()
		}

		throw OperationNotImplementedException(
			"Fant ${arenaDeltakere.size} deltakere som matcher med hist deltaker ${arenaHistDeltaker.HIST_TILTAKDELTAKER_ID}",
		)
	}

	private fun AmtDeltaker.validerGyldigHistDeltaker() {
		if (!status.erAvsluttende()) {
			throw IllegalStateException("Hist deltaker har fått status $status")
		}
		if (statusEndretDato == null) {
			throw ValidationException(
				"Kan ikke sende videre hist-deltaker $id fordi den mangler statusEndretDato som vil utledes til LocalDateTime.now()",
			)
		}
	}
}
