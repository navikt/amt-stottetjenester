package no.nav.amt.person.service.person

import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.getUUID
import no.nav.amt.person.service.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PersonRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun get(id: UUID): PersonDbo = template
        .query(
            "SELECT * FROM person WHERE id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).first()

    fun get(personident: String): PersonDbo? {
        val sql =
            """
            SELECT *
            FROM person
            WHERE EXISTS (
            	SELECT 1
            	FROM personident
            	WHERE
            	  	personident.ident = :personident
            		AND personident.person_id = person.id
            )
            """.trimIndent()

        return template
            .query(
                sql,
                sqlParameters("personident" to personident),
                rowMapper,
            ).firstOrNull()
    }

    fun upsert(person: PersonDbo) {
        val sql =
            """
            INSERT INTO person (
            	id,
            	personident,
            	er_falsk_identitet,
            	fornavn,
            	mellomnavn,
            	etternavn
            )
            VALUES (
            	:id,
            	:personident,
            	:er_falsk_identitet,
            	:fornavn,
            	:mellomnavn,
            	:etternavn
            )
            ON CONFLICT (id) DO UPDATE SET
            	er_falsk_identitet = :er_falsk_identitet,
            	fornavn = :fornavn,
            	mellomnavn = :mellomnavn,
            	etternavn = :etternavn,
            	personident = :personident,
            	modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val parameters = sqlParameters(
            "id" to person.id,
            "personident" to person.personident,
            "er_falsk_identitet" to person.erFalskIdentitet,
            "fornavn" to person.fornavn,
            "mellomnavn" to person.mellomnavn,
            "etternavn" to person.etternavn,
        )

        template.update(sql, parameters)
    }

    fun getPersoner(identer: Set<String>): List<PersonDbo> {
        if (identer.isEmpty()) return emptyList()

        val sql =
            """
            SELECT *
            FROM person
            WHERE EXISTS (
            	SELECT 1
            	FROM personident
            	WHERE
            	  	personident.ident IN (:identer)
            		AND personident.person_id = person.id
            )
            """.trimIndent()

        return template.query(
            sql,
            sqlParameters("identer" to identer),
            rowMapper,
        )
    }

    fun getAll(
        offset: Int,
        limit: Int = 500,
    ): List<PersonDbo> = template.query(
        "SELECT * FROM person ORDER BY id LIMIT :limit OFFSET :offset",
        sqlParameters(
            "offset" to offset,
            "limit" to limit,
        ),
        rowMapper,
    )

    fun getAllWithRolle(
        offset: Int,
        limit: Int = 500,
        rolle: Rolle,
    ): List<PersonDbo> {
        val sql =
            """
            SELECT *
            FROM person
            WHERE EXISTS (
            	SELECT 1
            	FROM person_rolle
            	WHERE
            		person_rolle.person_id = person.id
            		AND person_rolle.type = :rolle
            )
            ORDER by id
            LIMIT :limit
            OFFSET :offset
            """.trimIndent()

        val parameters = sqlParameters(
            "offset" to offset,
            "limit" to limit,
            "rolle" to rolle.name,
        )

        return template.query(sql, parameters, rowMapper)
    }

    // benyttes kun i tester
    internal fun delete(id: UUID) {
        val parameters = sqlParameters("id" to id)

        template.update(
            "DELETE FROM personident WHERE person_id = :id",
            parameters,
        )

        template.update(
            "DELETE FROM person WHERE id = :id",
            parameters,
        )
    }

    companion object {
        private val rowMapper = RowMapper { rs, _ ->
            PersonDbo(
                id = rs.getUUID("id"),
                personident = rs.getString("personident"),
                erFalskIdentitet = rs.getBoolean("er_falsk_identitet"),
                fornavn = rs.getString("fornavn"),
                mellomnavn = rs.getString("mellomnavn"),
                etternavn = rs.getString("etternavn"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                modifiedAt = rs.getTimestamp("modified_at").toLocalDateTime(),
            )
        }
    }
}
