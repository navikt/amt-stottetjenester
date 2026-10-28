package no.nav.amt.person.service.navenhet

import no.nav.amt.person.service.utils.getUUID
import no.nav.amt.person.service.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class NavEnhetRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun insert(input: NavEnhetDbo) {
        template.update(
            "INSERT INTO nav_enhet (id, nav_enhet_id, navn) VALUES (:id, :enhetId, :navn)",
            sqlParameters(
                "id" to input.id,
                "enhetId" to input.enhetId,
                "navn" to input.navn,
            ),
        )
    }

    fun get(id: UUID): NavEnhetDbo = template
        .query(
            "SELECT * FROM nav_enhet WHERE id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).firstOrNull()
        ?: throw NoSuchElementException("Enhet med id $id eksisterer ikke.")

    fun get(enhetId: String): NavEnhetDbo? = template
        .query(
            "SELECT * FROM nav_enhet WHERE nav_enhet_id = :enhetId",
            sqlParameters("enhetId" to enhetId),
            rowMapper,
        ).firstOrNull()

    fun getAll(): List<NavEnhetDbo> = template.query("SELECT * FROM nav_enhet", rowMapper)

    fun update(enhet: NavEnhetDbo) {
        template.update(
            "UPDATE nav_enhet SET navn = :navn, modified_at = CURRENT_TIMESTAMP WHERE id = :id",
            sqlParameters(
                "navn" to enhet.navn,
                "id" to enhet.id,
            ),
        )
    }

    companion object {
        private val rowMapper = RowMapper { rs, _ ->
            NavEnhetDbo(
                id = rs.getUUID("id"),
                enhetId = rs.getString("nav_enhet_id"),
                navn = rs.getString("navn"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                modifiedAt = rs.getTimestamp("modified_at").toLocalDateTime(),
            )
        }
    }
}
