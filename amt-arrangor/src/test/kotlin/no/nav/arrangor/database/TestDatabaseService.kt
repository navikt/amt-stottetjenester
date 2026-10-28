package no.nav.arrangor.database

import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.domain.AnsattRolle
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class TestDatabaseService(
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
    private val arrangorRepository: ArrangorRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertAnsatt(
        personident: String = UUID.randomUUID().toString(),
        personId: UUID = UUID.randomUUID(),
        fornavn: String = UUID.randomUUID().toString(),
        mellomnavn: String? = UUID.randomUUID().toString(),
        etternavn: String = UUID.randomUUID().toString(),
        arrangorer: List<ArrangorDbo>,
        lastSynchronized: LocalDateTime = LocalDateTime.now(),
    ): AnsattDbo {
        val ansatt = ansattRepository.insertOrUpdate(
            ansatt(personident, personId, fornavn, mellomnavn, etternavn, arrangorer, lastSynchronized),
        )
        arrangorer.forEach { arrangor ->
            if (arrangorRepository.get(arrangor.arrangorId) == null) {
                arrangorRepository.insertOrUpdate(
                    ArrangorRepository.ArrangorDbo(
                        id = arrangor.arrangorId,
                        navn = arrangor.arrangorId.toString(),
                        organisasjonsnummer = UUID.randomUUID().toString(),
                        overordnetArrangorId = null,
                    ),
                )
            }
            arrangor.veileder
                .map { it.deltakerId }
                .distinct()
                .forEach { insertDeltaker(it) }
        }
        ansattArrangorRepository.replaceForAnsatt(ansatt.id, arrangorer)
        return checkNotNull(ansattRepository.get(ansatt.id))
    }

    fun setLastSynchronized(
        ansattId: UUID,
        timestamp: LocalDateTime,
    ) {
        jdbcTemplate.update(
            "UPDATE ansatt SET last_synchronized = ? WHERE id = ?",
            timestamp,
            ansattId,
        )
    }

    fun insertDeltaker(deltakerId: UUID = UUID.randomUUID()): UUID {
        jdbcTemplate.update(
            """
            INSERT INTO deltaker (id, statustype, gyldig_fra, opprettet_dato)
            VALUES (?, 'IKKE_AKTUELL', current_timestamp, current_timestamp)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            deltakerId,
        )
        return deltakerId
    }

    fun insertArrangor(
        navn: String = UUID.randomUUID().toString(),
        organisasjonsnummer: String = UUID.randomUUID().toString(),
        overordnetArrangorId: UUID? = null,
    ): ArrangorRepository.ArrangorDbo = arrangor(navn, organisasjonsnummer, overordnetArrangorId)
        .let { arrangorRepository.insertOrUpdate(it) }

    fun ansatt(
        personident: String = UUID.randomUUID().toString(),
        personId: UUID = UUID.randomUUID(),
        fornavn: String = UUID.randomUUID().toString(),
        mellomnavn: String? = UUID.randomUUID().toString(),
        etternavn: String = UUID.randomUUID().toString(),
        arrangorer: List<ArrangorDbo> =
            listOf(ArrangorDbo(UUID.randomUUID(), listOf(RolleDbo(AnsattRolle.KOORDINATOR)), emptyList(), emptyList())),
        lastSynchronized: LocalDateTime = LocalDateTime.now(),
    ): AnsattDbo = AnsattDbo(
        id = UUID.randomUUID(),
        personident = personident,
        personId = personId,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        arrangorer = arrangorer,
        lastSynchronized = lastSynchronized,
    )

    fun arrangor(
        navn: String = UUID.randomUUID().toString(),
        organisasjonsnummer: String = UUID.randomUUID().toString(),
        overordnetArrangorId: UUID? = null,
    ): ArrangorRepository.ArrangorDbo = ArrangorRepository.ArrangorDbo(
        id = UUID.randomUUID(),
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId,
    )

    fun ansattArrangorDbo(
        arrangorId: UUID = UUID.randomUUID(),
        roller: List<RolleDbo> = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
        veileder: List<VeilederDeltakerDbo> = listOf(),
        koordinator: List<KoordinatorsDeltakerlisteDbo> =
            listOf(
                KoordinatorsDeltakerlisteDbo(UUID.randomUUID()),
            ),
    ) = ArrangorDbo(
        arrangorId = arrangorId,
        roller = roller,
        veileder = veileder,
        koordinator = koordinator,
    )
}
