package no.nav.arrangor.deltaker

import no.nav.arrangor.kafka.model.Deltaker
import no.nav.arrangor.kafka.model.DeltakerStatus
import no.nav.arrangor.kafka.model.DeltakerStatusType
import no.nav.arrangor.utils.sqlParameters
import no.nav.arrangor.utils.toSystemZoneLocalDateTime
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DeltakerRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        Deltaker(
            id = UUID.fromString(rs.getString("id")),
            status = DeltakerStatus(
                type = DeltakerStatusType.valueOf(rs.getString("statustype")),
                gyldigFra = rs.getTimestamp("gyldig_fra").toSystemZoneLocalDateTime(),
                opprettetDato = rs.getTimestamp("opprettet_dato").toSystemZoneLocalDateTime(),
            ),
        )
    }

    fun get(id: UUID): Deltaker? = template
        .query(
            "SELECT * FROM deltaker WHERE id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).firstOrNull()

    fun insertOrUpdate(deltaker: Deltaker) {
        val sql =
            """
            INSERT INTO deltaker(id, statustype, gyldig_fra, opprettet_dato)
            VALUES (:id,
            		:statustype,
                    :gyldig_fra,
                    :opprettet_dato)
            ON CONFLICT (id) DO UPDATE SET
            	statustype		  = :statustype,
            	gyldig_fra		  = :gyldig_fra,
            	opprettet_dato    = :opprettet_dato
            """.trimIndent()

        template.update(
            sql,
            sqlParameters(
                "id" to deltaker.id,
                "statustype" to deltaker.status.type.name,
                "gyldig_fra" to deltaker.status.gyldigFra,
                "opprettet_dato" to deltaker.status.opprettetDato,
            ),
        )
    }
}
