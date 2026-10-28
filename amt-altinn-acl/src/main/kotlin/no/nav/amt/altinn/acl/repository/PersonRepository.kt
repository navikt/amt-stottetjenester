package no.nav.amt.altinn.acl.repository

import no.nav.amt.altinn.acl.repository.dbo.PersonDbo
import no.nav.amt.altinn.acl.utils.DbUtils.sqlParameters
import no.nav.amt.altinn.acl.utils.getZonedDateTime
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZonedDateTime

@Repository
class PersonRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun setSynchronized(
        norskIdent: String,
        lastSynchronized: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val sql =
            """
            UPDATE person
            SET last_synchronized = :last_synchronized
            WHERE norsk_ident = :norsk_ident
            """.trimIndent()

        template.update(
            sql,
            sqlParameters(
                "norsk_ident" to norskIdent,
                "last_synchronized" to lastSynchronized.toOffsetDateTime(),
            ),
        )
    }

    fun getUnsynchronizedPersons(
        maxSize: Int,
        synchronizedBefore: OffsetDateTime,
    ): List<PersonDbo> {
        val sql =
            """
            SELECT 
                id,
                norsk_ident,
                created,
                last_synchronized,
                synchronization_attempt,
                applied_synchronization_attempt
            FROM person
            WHERE last_synchronized < :synchronized_before
            ORDER BY last_synchronized
            limit :limit
            """.trimIndent()

        val parameters = sqlParameters(
            "limit" to maxSize,
            "synchronized_before" to synchronizedBefore,
        )

        return template.query(sql, parameters, rowMapper)
    }

    fun get(norskIdent: String): PersonDbo? = template
        .query(
            """
            SELECT 
                id,
                norsk_ident,
                created,
                last_synchronized,
                synchronization_attempt,
                applied_synchronization_attempt
            FROM person
            WHERE norsk_ident = :norsk_ident
            """.trimIndent(),
            sqlParameters("norsk_ident" to norskIdent),
            rowMapper,
        ).firstOrNull()

    fun reserveSynchronizationAttempt(personId: Long): Long = template.queryForObject(
        """
        UPDATE person
        SET synchronization_attempt = synchronization_attempt + 1
        WHERE id = :person_id
        RETURNING synchronization_attempt
        """.trimIndent(),
        sqlParameters("person_id" to personId),
        Long::class.java,
    ) ?: error("Fant ikke synchronization attempt")

    fun lockForUpdate(personId: Long): PersonDbo = template.queryForObject(
        """
        SELECT
            id,
            norsk_ident,
            created,
            last_synchronized,
            synchronization_attempt,
            applied_synchronization_attempt
        FROM person
        WHERE id = :person_id
        FOR UPDATE
        """.trimIndent(),
        sqlParameters("person_id" to personId),
        rowMapper,
    )

    fun completeSynchronization(
        personId: Long,
        norskIdent: String,
        synchronizationAttempt: Long,
        lastSynchronized: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val updatedRows = template.update(
            """
            UPDATE person
            SET
                last_synchronized = :last_synchronized,
                applied_synchronization_attempt = :synchronization_attempt
            WHERE id = :person_id
                AND norsk_ident = :norsk_ident
                AND applied_synchronization_attempt < :synchronization_attempt
            """.trimIndent(),
            sqlParameters(
                "person_id" to personId,
                "norsk_ident" to norskIdent,
                "synchronization_attempt" to synchronizationAttempt,
                "last_synchronized" to lastSynchronized.toOffsetDateTime(),
            ),
        )

        check(updatedRows == 1) {
            "Kunne ikke fullføre synkroniseringsforsøk $synchronizationAttempt for person $personId"
        }
    }

    fun createAndSetSynchronized(
        norskIdent: String,
        lastSynchronized: ZonedDateTime = ZonedDateTime.now(),
    ): PersonDbo {
        val sql =
            """
            INSERT INTO person(
                norsk_ident, 
                last_synchronized
            )
            VALUES (
                :norsk_ident, 
                :last_synchronized
            )
            RETURNING *
            """.trimIndent()

        val params = sqlParameters(
            "norsk_ident" to norskIdent,
            "last_synchronized" to lastSynchronized.toOffsetDateTime(),
        )

        return template.queryForObject(sql, params, rowMapper)
    }

    private val rowMapper = RowMapper { rs, _ ->
        PersonDbo(
            id = rs.getLong("id"),
            norskIdent = rs.getString("norsk_ident"),
            created = rs.getZonedDateTime("created"),
            lastSynchronized = rs.getZonedDateTime("last_synchronized"),
            synchronizationAttempt = rs.getLong("synchronization_attempt"),
            appliedSynchronizationAttempt = rs.getLong("applied_synchronization_attempt"),
        )
    }
}
