package no.nav.amt.person.service.person

import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RolleRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun insert(
        personId: UUID,
        rolle: Rolle,
    ) {
        val sql =
            """
            INSERT INTO person_rolle (
            	id,
            	person_id,
            	type
            )
            VALUES (
            	:id,
            	:personId,
            	:rolle
            )
            ON CONFLICT (person_id, type) DO NOTHING
            """.trimIndent()

        val parameters = sqlParameters(
            "id" to UUID.randomUUID(),
            "personId" to personId,
            "rolle" to rolle.name,
        )

        template.update(sql, parameters)
    }

    fun harRolle(
        personId: UUID,
        rolle: Rolle,
    ): Boolean = template.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM person_rolle WHERE person_id = :personId AND type = :rolle)",
        sqlParameters(
            "personId" to personId,
            "rolle" to rolle.name,
        ),
        Boolean::class.java,
    ) ?: throw IllegalStateException("Kall til harRolle $rolle feilet")

    fun delete(
        personId: UUID,
        rolle: Rolle,
    ) {
        template.update(
            "DELETE FROM person_rolle WHERE person_id = :personId AND type = :rolle",
            sqlParameters(
                "personId" to personId,
                "rolle" to rolle.name,
            ),
        )
    }
}
