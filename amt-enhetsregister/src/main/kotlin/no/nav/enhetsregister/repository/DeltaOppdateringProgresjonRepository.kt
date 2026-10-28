package no.nav.enhetsregister.repository

import no.nav.enhetsregister.domain.EnhetType
import no.nav.enhetsregister.repository.model.DeltaEnhetOppdateringProgresjon
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class DeltaOppdateringProgresjonRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun hentOppdateringProgresjon(enhetType: EnhetType): DeltaEnhetOppdateringProgresjon {
        val sql =
            """
            SELECT
                $OPPDATERING_ID,
                $ENHET_TYPE
            FROM $TABLE_NAME
            WHERE $ENHET_TYPE = ?::enhet_type
            """.trimIndent()

        return jdbcTemplate.query(sql, rowMapper, enhetType.toDatabaseValue()).first()
    }

    fun oppdaterProgresjon(
        enhetType: EnhetType,
        oppdateringId: Int,
    ) {
        val sql =
            """
            UPDATE $TABLE_NAME 
            SET 
                $OPPDATERING_ID = ?, 
                $SISTE_OPPDATERING = CURRENT_TIMESTAMP 
            WHERE $ENHET_TYPE = ?::enhet_type
            """.trimIndent()

        jdbcTemplate.update(sql, oppdateringId, enhetType.toDatabaseValue())
    }

    private companion object Table {
        const val TABLE_NAME = "delta_enhet_oppdatering"

        const val OPPDATERING_ID = "oppdatering_id"
        const val ENHET_TYPE = "enhet_type"
        const val SISTE_OPPDATERING = "siste_oppdatering"

        val rowMapper = RowMapper { rs, _ ->
            DeltaEnhetOppdateringProgresjon(
                oppdateringId = rs.getInt(OPPDATERING_ID),
                enhetType = rs.getString(ENHET_TYPE).toDomainEnhetType(),
            )
        }

        fun EnhetType.toDatabaseValue(): String = when (this) {
            EnhetType.MODERENHET -> "MODERENHET"
            EnhetType.UNDERENHET -> "UNDERENHET"
        }

        fun String.toDomainEnhetType(): EnhetType = when (this) {
            "MODERENHET" -> EnhetType.MODERENHET
            "UNDERENHET" -> EnhetType.UNDERENHET
            else -> throw IllegalArgumentException("Ukjent enhet_type i databasen: $this")
        }
    }
}
