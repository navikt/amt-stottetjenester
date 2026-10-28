package no.nav.arrangor.ansatt.repository

import no.nav.arrangor.utils.sqlParameters
import no.nav.arrangor.utils.toSystemZoneLocalDateTime
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Alle lesemetoder returnerer [AnsattDbo] med arrangørrelasjoner fra de normaliserte tabellene,
 * slik at rollesynk og tilgangskontroll aldri får en ufullstendig ansatt.
 * Relasjoner skrives via [AnsattArrangorRepository], ikke her.
 */
@Repository
class AnsattRepository(
    private val template: NamedParameterJdbcTemplate,
    private val ansattArrangorRepository: AnsattArrangorRepository,
) {
    private val rowMapper = RowMapper { rs, _ ->
        AnsattDbo(
            id = UUID.fromString(rs.getString("id")),
            personId = UUID.fromString(rs.getString("person_id")),
            personident = rs.getString("personident"),
            fornavn = rs.getString("fornavn"),
            mellomnavn = rs.getString("mellomnavn"),
            etternavn = rs.getString("etternavn"),
            arrangorer = emptyList(),
            modifiedAt = rs.getTimestamp("modified_at").toSystemZoneLocalDateTime(),
            lastSynchronized = rs.getTimestamp("last_synchronized").toSystemZoneLocalDateTime(),
        )
    }

    fun insertOrUpdate(ansatt: AnsattDbo): AnsattDbo {
        val sql =
            """
            INSERT INTO ansatt(
                id,
                person_id,
                personident,
                fornavn,
                mellomnavn,
                etternavn,
                modified_at,
                last_synchronized
            )
            VALUES (
                :id,
                :person_id,
                :personident,
                :fornavn,
                :mellomnavn,
                :etternavn,
                :modified_at,
                :last_synchronized
            )
            ON CONFLICT (person_id) DO UPDATE SET
                personident       = EXCLUDED.personident,
                fornavn           = EXCLUDED.fornavn,
                mellomnavn        = EXCLUDED.mellomnavn,
                etternavn         = EXCLUDED.etternavn,
                modified_at       = current_timestamp,
                last_synchronized = EXCLUDED.last_synchronized
            RETURNING *
            """.trimIndent()

        return template
            .query(
                sql,
                sqlParameters(
                    "id" to ansatt.id,
                    "person_id" to (ansatt.personId),
                    "personident" to ansatt.personident,
                    "fornavn" to ansatt.fornavn,
                    "mellomnavn" to ansatt.mellomnavn,
                    "etternavn" to ansatt.etternavn,
                    "modified_at" to ansatt.modifiedAt,
                    "last_synchronized" to ansatt.lastSynchronized,
                ),
                rowMapper,
            ).first()
            .medArrangorer()
    }

    /** Oppdaterer bare personalia; tilganger lagres separat i normaliserte tabeller. */
    fun updatePersonalia(
        ansattId: UUID,
        personident: String,
        fornavn: String,
        mellomnavn: String?,
        etternavn: String,
    ): Boolean = template.update(
        """
        UPDATE ansatt
        SET personident = :personident,
            fornavn = :fornavn,
            mellomnavn = :mellomnavn,
            etternavn = :etternavn,
            modified_at = current_timestamp
        WHERE id = :ansattId
        """.trimIndent(),
        sqlParameters(
            "ansattId" to ansattId,
            "personident" to personident,
            "fornavn" to fornavn,
            "mellomnavn" to mellomnavn,
            "etternavn" to etternavn,
        ),
    ) == 1

    fun get(id: UUID): AnsattDbo? = template
        .query(
            "SELECT * FROM ansatt WHERE id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).firstOrNull()
        ?.medArrangorer()

    fun getAnsatte(ider: List<UUID>): List<AnsattDbo> {
        if (ider.isEmpty()) {
            return emptyList()
        }
        return template
            .query(
                "SELECT * FROM ansatt WHERE id in(:ids)",
                sqlParameters("ids" to ider),
                rowMapper,
            ).medArrangorer()
    }

    fun getAnsatteHosArrangor(arrangorId: UUID): List<AnsattDbo> = getAnsatte(ansattArrangorRepository.getAnsattIderForArrangor(arrangorId))

    fun get(personident: String): AnsattDbo? = template
        .query(
            "SELECT * from ansatt where personident = :personident",
            sqlParameters("personident" to personident),
            rowMapper,
        ).firstOrNull()
        ?.medArrangorer()

    fun getIdForPersonident(personident: String): UUID? = template
        .queryForList(
            "SELECT id FROM ansatt WHERE personident = :personident",
            sqlParameters("personident" to personident),
            UUID::class.java,
        ).firstOrNull()

    fun getToSynchronize(
        maxSize: Int,
        synchronizedBefore: LocalDateTime,
    ): List<AnsattDbo> {
        val sql =
            """
            SELECT *
            FROM ansatt
            WHERE last_synchronized < :synchronized_before
            ORDER BY last_synchronized
            limit :limit
            """.trimIndent()

        val parameters =
            sqlParameters(
                "limit" to maxSize,
                "synchronized_before" to synchronizedBefore,
            )

        return template.query(sql, parameters, rowMapper).medArrangorer()
    }

    fun getAll(
        offset: Int,
        limit: Int,
    ): List<AnsattDbo> {
        val sql =
            """
            SELECT *
            FROM ansatt
            ORDER BY modified_at
            OFFSET :offset
            LIMIT :limit
            """.trimIndent()

        val parameters = sqlParameters(
            "offset" to offset,
            "limit" to limit,
        )
        return template.query(sql, parameters, rowMapper).medArrangorer()
    }

    fun getByPersonId(personId: UUID) = template
        .query(
            "SELECT * FROM ansatt WHERE person_id = :personId",
            sqlParameters("personId" to personId),
            rowMapper,
        ).firstOrNull()
        ?.medArrangorer()

    private fun AnsattDbo.medArrangorer(): AnsattDbo = copy(arrangorer = ansattArrangorRepository.getArrangorerForAnsatt(id))

    private fun List<AnsattDbo>.medArrangorer(): List<AnsattDbo> {
        if (isEmpty()) return this
        val arrangorerPerAnsatt = ansattArrangorRepository.getArrangorerForAnsatte(map { it.id })
        return map { it.copy(arrangorer = arrangorerPerAnsatt[it.id].orEmpty()) }
    }
}
