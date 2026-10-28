package no.nav.amt.person.service.navansatt

import no.nav.amt.person.service.utils.getNullableUUID
import no.nav.amt.person.service.utils.getUUID
import no.nav.amt.person.service.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class NavAnsattRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun get(id: UUID): NavAnsattDbo = template
        .query(
            "SELECT * FROM nav_ansatt WHERE id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).first()

    fun get(navIdent: String): NavAnsattDbo? = template
        .query(
            "SELECT * FROM nav_ansatt WHERE nav_ident = :navIdent",
            sqlParameters("navIdent" to navIdent),
            rowMapper,
        ).firstOrNull()

    fun getAll(): List<NavAnsattDbo> = template.query("SELECT * FROM nav_ansatt", rowMapper)

    fun upsert(navAnsatt: NavAnsattDbo): NavAnsattDbo = template.queryForObject(
        "$UPSERT_SQL RETURNING *",
        upsertParamsFromNavAnsatt(navAnsatt),
        rowMapper,
    )

    fun upsertMany(ansatte: Set<NavAnsattDbo>) {
        template.batchUpdate(
            UPSERT_SQL,
            ansatte
                .map { navAnsatt -> upsertParamsFromNavAnsatt(navAnsatt) }
                .toTypedArray(),
        )
    }

    companion object {
        private val rowMapper = RowMapper { rs, _ ->
            NavAnsattDbo(
                id = rs.getUUID("id"),
                navIdent = rs.getString("nav_ident"),
                navn = rs.getString("navn"),
                telefon = rs.getString("telefon"),
                epost = rs.getString("epost"),
                navEnhetId = rs.getNullableUUID("nav_enhet_id"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                modifiedAt = rs.getTimestamp("modified_at").toLocalDateTime(),
            )
        }

        private fun upsertParamsFromNavAnsatt(navAnsatt: NavAnsattDbo) = sqlParameters(
            "id" to navAnsatt.id,
            "navIdent" to navAnsatt.navIdent,
            "navn" to navAnsatt.navn,
            "telefon" to navAnsatt.telefon,
            "epost" to navAnsatt.epost,
            "nav_enhet_id" to navAnsatt.navEnhetId,
        )

        private val UPSERT_SQL =
            """
            INSERT INTO nav_ansatt (
            	id,
            	nav_ident,
            	navn,
            	telefon,
            	epost,
            	nav_enhet_id
            )
            VALUES (
            	:id,
            	:navIdent,
            	:navn,
            	:telefon,
            	:epost,
            	:nav_enhet_id
            )
            ON CONFLICT (nav_ident) DO UPDATE SET
            	navn = :navn,
            	telefon = :telefon,
            	epost = :epost,
            	nav_enhet_id = :nav_enhet_id,
            	modified_at = CURRENT_TIMESTAMP
            """.trimIndent()
    }
}
