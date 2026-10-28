package no.nav.arrangor.ansatt.repository

import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.getNullableZonedDateTime
import no.nav.arrangor.utils.sqlParameters
import no.nav.arrangor.utils.toSqlOffsetDateTime
import no.nav.arrangor.utils.toSystemZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

/** Persistens og lesing av roller og tilganger fra de normaliserte ansatt-arrangør-tabellene. */
@Repository
class AnsattArrangorRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val arrangorRowMapper = RowMapper { rs, _ ->
        AnsattArrangorRad(
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
        )
    }

    private val rolleRowMapper = RowMapper { rs, _ ->
        RolleRad(
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
            rolle = RolleDbo(
                rolle = AnsattRolle.valueOf(rs.getString("rolle")),
                gyldigFra = rs.getTimestamp("gyldig_fra").toSystemZonedDateTime(),
                gyldigTil = rs.getNullableZonedDateTime("gyldig_til"),
            ),
        )
    }

    private val veilederRowMapper = RowMapper { rs, _ ->
        VeilederRad(
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
            veileder = VeilederDeltakerDbo(
                deltakerId = UUID.fromString(rs.getString("deltaker_id")),
                veilederType = VeilederType.valueOf(rs.getString("veileder_type")),
                gyldigFra = rs.getTimestamp("gyldig_fra").toSystemZonedDateTime(),
                gyldigTil = rs.getNullableZonedDateTime("gyldig_til"),
            ),
        )
    }

    private val koordinatorRowMapper = RowMapper { rs, _ ->
        KoordinatorRad(
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
            koordinator = KoordinatorsDeltakerlisteDbo(
                deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
                gyldigFra = rs.getTimestamp("gyldig_fra").toSystemZonedDateTime(),
                gyldigTil = rs.getNullableZonedDateTime("gyldig_til"),
            ),
        )
    }

    /**
     * Initialiserer hele den normaliserte tilstanden når en ny ansatt opprettes etter backfill.
     * Senere endringer bruker de operasjonsspesifikke metodene under.
     */
    fun replaceForAnsatt(
        ansattId: UUID,
        arrangorer: List<ArrangorDbo>,
    ) {
        deleteForAnsatt(ansattId)

        if (arrangorer.isEmpty()) {
            return
        }

        insertAnsattArrangor(ansattId, arrangorer.map { it.arrangorId }.distinct())
        insertRoller(ansattId, arrangorer)
        insertVeiledere(ansattId, arrangorer)
        insertKoordinatorer(ansattId, arrangorer)
    }

    fun insertRolle(
        ansattId: UUID,
        arrangorId: UUID,
        rolle: RolleDbo,
    ) {
        ensureAnsattArrangorer(ansattId, setOf(arrangorId))
        insertRoller(
            ansattId,
            listOf(ArrangorDbo(arrangorId, listOf(rolle), emptyList(), emptyList())),
        )
    }

    fun deaktiverRolle(
        ansattId: UUID,
        arrangorId: UUID,
        rolle: RolleDbo,
    ) {
        val updatedRows = template.update(
            """
            UPDATE ansatt_arrangor_rolle
            SET gyldig_til = :gyldigTil,
                modified_at = current_timestamp
            WHERE ansatt_id = :ansattId
              AND arrangor_id = :arrangorId
              AND rolle = :rolle
              AND gyldig_fra = :gyldigFra
            """.trimIndent(),
            sqlParameters(
                "ansattId" to ansattId,
                "arrangorId" to arrangorId,
                "rolle" to rolle.rolle.name,
                "gyldigFra" to rolle.gyldigFra.toSqlOffsetDateTime(),
                "gyldigTil" to rolle.gyldigTil?.toSqlOffsetDateTime(),
            ),
        )
        if (updatedRows == 0) {
            logger.warn("Fant ikke rolle som skulle deaktiveres for ansatt $ansattId hos arrangør $arrangorId")
        }
    }

    fun insertVeileder(
        ansattId: UUID,
        arrangorId: UUID,
        veileder: VeilederDeltakerDbo,
    ) {
        ensureAnsattArrangorer(ansattId, setOf(arrangorId))
        insertVeiledere(
            ansattId,
            listOf(ArrangorDbo(arrangorId, emptyList(), listOf(veileder), emptyList())),
        )
    }

    fun deaktiverVeileder(
        ansattId: UUID,
        arrangorId: UUID,
        veileder: VeilederDeltakerDbo,
    ) {
        val updatedRows = template.update(
            """
            UPDATE ansatt_arrangor_veileder
            SET gyldig_til = :gyldigTil,
                modified_at = current_timestamp
            WHERE ansatt_id = :ansattId
              AND arrangor_id = :arrangorId
              AND deltaker_id = :deltakerId
              AND veileder_type = :veilederType
              AND gyldig_fra = :gyldigFra
            """.trimIndent(),
            sqlParameters(
                "ansattId" to ansattId,
                "arrangorId" to arrangorId,
                "deltakerId" to veileder.deltakerId,
                "veilederType" to veileder.veilederType.name,
                "gyldigFra" to veileder.gyldigFra.toSqlOffsetDateTime(),
                "gyldigTil" to veileder.gyldigTil?.toSqlOffsetDateTime(),
            ),
        )
        if (updatedRows == 0) {
            logger.warn("Fant ikke veilederkobling som skulle deaktiveres for ansatt $ansattId hos arrangør $arrangorId")
        }
    }

    fun insertKoordinator(
        ansattId: UUID,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ) {
        ensureAnsattArrangorer(ansattId, setOf(arrangorId))
        insertKoordinatorer(
            ansattId = ansattId,
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorId = arrangorId,
                    roller = emptyList(),
                    veileder = emptyList(),
                    koordinator = listOf(koordinator),
                ),
            ),
        )
    }

    fun deaktiverKoordinator(
        ansattId: UUID,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ) {
        val updatedRows = template.update(
            """
            UPDATE ansatt_arrangor_koordinator
            SET gyldig_til = :gyldigTil,
                modified_at = current_timestamp
            WHERE ansatt_id = :ansattId
              AND arrangor_id = :arrangorId
              AND deltakerliste_id = :deltakerlisteId
              AND gyldig_fra = :gyldigFra
            """.trimIndent(),
            sqlParameters(
                "ansattId" to ansattId,
                "arrangorId" to arrangorId,
                "deltakerlisteId" to koordinator.deltakerlisteId,
                "gyldigFra" to koordinator.gyldigFra.toSqlOffsetDateTime(),
                "gyldigTil" to koordinator.gyldigTil?.toSqlOffsetDateTime(),
            ),
        )
        if (updatedRows == 0) {
            logger.warn("Fant ikke koordinatorkobling som skulle deaktiveres for ansatt $ansattId hos arrangør $arrangorId")
        }
    }

    /** Sletter alle rader for [ansattId]. Barnetabellene ryddes automatisk via `ON DELETE CASCADE`. */
    fun deleteForAnsatt(ansattId: UUID) {
        template.update("DELETE FROM ansatt_arrangor WHERE ansatt_id = :ansattId", sqlParameters("ansattId" to ansattId))
    }

    fun getArrangorerForAnsatt(ansattId: UUID): List<ArrangorDbo> = getArrangorerForAnsatte(listOf(ansattId))[ansattId].orEmpty()

    /**
     * Leser normalisert tilstand for flere ansatte med fire spørringer uavhengig av antall ansatte.
     * En ansatt uten foreldrerader får en tom liste; det gjøres ikke fallback til jsonb.
     */
    fun getArrangorerForAnsatte(ansattIder: Collection<UUID>): Map<UUID, List<ArrangorDbo>> {
        val unikeAnsattIder = ansattIder.distinct()
        if (unikeAnsattIder.isEmpty()) return emptyMap()

        val parameters = sqlParameters("ansattIder" to unikeAnsattIder)
        val arrangorer = template
            .query(
                """
                SELECT ansatt_id, arrangor_id
                FROM ansatt_arrangor
                WHERE ansatt_id IN (:ansattIder)
                ORDER BY ansatt_id, arrangor_id
                """.trimIndent(),
                parameters,
                arrangorRowMapper,
            ).groupBy { it.ansattId }

        val roller = template
            .query(
                """
                SELECT ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til
                FROM ansatt_arrangor_rolle
                WHERE ansatt_id IN (:ansattIder)
                ORDER BY ansatt_id, arrangor_id, gyldig_fra, rolle
                """.trimIndent(),
                parameters,
                rolleRowMapper,
            ).groupBy { it.ansattId to it.arrangorId }

        val veiledere = template
            .query(
                """
                SELECT ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra, gyldig_til
                FROM ansatt_arrangor_veileder
                WHERE ansatt_id IN (:ansattIder)
                ORDER BY ansatt_id, arrangor_id, gyldig_fra, deltaker_id, veileder_type
                """.trimIndent(),
                parameters,
                veilederRowMapper,
            ).groupBy { it.ansattId to it.arrangorId }

        val koordinatorer = template
            .query(
                """
                SELECT ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra, gyldig_til
                FROM ansatt_arrangor_koordinator
                WHERE ansatt_id IN (:ansattIder)
                ORDER BY ansatt_id, arrangor_id, gyldig_fra, deltakerliste_id
                """.trimIndent(),
                parameters,
                koordinatorRowMapper,
            ).groupBy { it.ansattId to it.arrangorId }

        return unikeAnsattIder.associateWith { ansattId ->
            arrangorer[ansattId].orEmpty().map { arrangor ->
                val key = ansattId to arrangor.arrangorId
                ArrangorDbo(
                    arrangorId = arrangor.arrangorId,
                    roller = roller[key].orEmpty().map { it.rolle },
                    veileder = veiledere[key].orEmpty().map { it.veileder },
                    koordinator = koordinatorer[key].orEmpty().map { it.koordinator },
                )
            }
        }
    }

    fun getAnsattIderForArrangor(arrangorId: UUID): List<UUID> = template.query(
        """
        SELECT ansatt_id
        FROM ansatt_arrangor
        WHERE arrangor_id = :arrangorId
        ORDER BY ansatt_id
        """.trimIndent(),
        sqlParameters("arrangorId" to arrangorId),
        RowMapper { rs, _ -> UUID.fromString(rs.getString("ansatt_id")) },
    )

    /**
     * Setter `gyldig_til` for alle fortsatt-gyldige veiledere for [deltakerId].
     * Returnerer ansatt-ID-ene som skal publiseres på nytt.
     */
    fun deaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        deaktiveringsdato: ZonedDateTime,
    ): List<UUID> {
        val sql =
            """
            UPDATE ansatt_arrangor_veileder
            SET gyldig_til = :deaktiveringsdato,
                modified_at = current_timestamp
            WHERE deltaker_id = :deltakerId
              AND gyldig_til IS NULL
            RETURNING ansatt_id
            """.trimIndent()

        return template.query(
            sql,
            sqlParameters(
                "deltakerId" to deltakerId,
                "deaktiveringsdato" to deaktiveringsdato.toSqlOffsetDateTime(),
            ),
            RowMapper { rs, _ -> UUID.fromString(rs.getString("ansatt_id")) },
        )
    }

    /**
     * Nullstiller `gyldig_til` for veiledere hvis gyldighet fortsatt lå frem i tid da
     * deaktiveringen ble angret.
     */
    fun maybeReaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        deaktiveringsdato: ZonedDateTime = ZonedDateTime.now(),
    ): List<UUID> {
        val sql =
            """
            UPDATE ansatt_arrangor_veileder
            SET gyldig_til = NULL,
                modified_at = current_timestamp
            WHERE deltaker_id = :deltakerId
              AND gyldig_til > :deaktiveringsdato
            RETURNING ansatt_id
            """.trimIndent()

        return template.query(
            sql,
            sqlParameters(
                "deltakerId" to deltakerId,
                "deaktiveringsdato" to deaktiveringsdato.toSqlOffsetDateTime(),
            ),
            RowMapper { rs, _ -> UUID.fromString(rs.getString("ansatt_id")) },
        )
    }

    private fun insertAnsattArrangor(
        ansattId: UUID,
        arrangorIder: List<UUID>,
    ) {
        val params = arrangorIder
            .map { sqlParameters("ansattId" to ansattId, "arrangorId" to it) }
            .toTypedArray<SqlParameterSource>()

        template.batchUpdate(
            """
            INSERT INTO ansatt_arrangor (ansatt_id, arrangor_id)
            VALUES (:ansattId, :arrangorId)
            """.trimIndent(),
            params,
        )
    }

    private fun ensureAnsattArrangorer(
        ansattId: UUID,
        arrangorIder: Collection<UUID>,
    ) {
        val params = arrangorIder
            .map { sqlParameters("ansattId" to ansattId, "arrangorId" to it) }
            .toTypedArray<SqlParameterSource>()

        if (params.isEmpty()) return

        template.batchUpdate(
            """
            INSERT INTO ansatt_arrangor (ansatt_id, arrangor_id)
            VALUES (:ansattId, :arrangorId)
            ON CONFLICT (ansatt_id, arrangor_id) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    private fun insertRoller(
        ansattId: UUID,
        arrangorer: List<ArrangorDbo>,
    ) {
        val params = arrangorer
            .flatMap { arrangor ->
                arrangor.roller.map { rolle ->
                    sqlParameters(
                        "ansattId" to ansattId,
                        "arrangorId" to arrangor.arrangorId,
                        "rolle" to rolle.rolle.name,
                        "gyldigFra" to rolle.gyldigFra.toSqlOffsetDateTime(),
                        "gyldigTil" to rolle.gyldigTil?.toSqlOffsetDateTime(),
                    )
                }
            }.toTypedArray<SqlParameterSource>()

        if (params.isEmpty()) return

        template.batchUpdate(
            """
            INSERT INTO ansatt_arrangor_rolle (ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til)
            VALUES (:ansattId, :arrangorId, :rolle, :gyldigFra, :gyldigTil)
            ON CONFLICT (ansatt_id, arrangor_id, rolle, gyldig_fra) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    private fun insertVeiledere(
        ansattId: UUID,
        arrangorer: List<ArrangorDbo>,
    ) {
        val params = arrangorer
            .flatMap { arrangor ->
                arrangor.veileder.map { veileder ->
                    sqlParameters(
                        "ansattId" to ansattId,
                        "arrangorId" to arrangor.arrangorId,
                        "deltakerId" to veileder.deltakerId,
                        "veilederType" to veileder.veilederType.name,
                        "gyldigFra" to veileder.gyldigFra.toSqlOffsetDateTime(),
                        "gyldigTil" to veileder.gyldigTil?.toSqlOffsetDateTime(),
                    )
                }
            }.toTypedArray<SqlParameterSource>()

        if (params.isEmpty()) return

        template.batchUpdate(
            """
            INSERT INTO ansatt_arrangor_veileder (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra, gyldig_til)
            VALUES (:ansattId, :arrangorId, :deltakerId, :veilederType, :gyldigFra, :gyldigTil)
            ON CONFLICT (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    private fun insertKoordinatorer(
        ansattId: UUID,
        arrangorer: List<ArrangorDbo>,
    ) {
        val params = arrangorer
            .flatMap { arrangor ->
                arrangor.koordinator.map { koordinator ->
                    sqlParameters(
                        "ansattId" to ansattId,
                        "arrangorId" to arrangor.arrangorId,
                        "deltakerlisteId" to koordinator.deltakerlisteId,
                        "gyldigFra" to koordinator.gyldigFra.toSqlOffsetDateTime(),
                        "gyldigTil" to koordinator.gyldigTil?.toSqlOffsetDateTime(),
                    )
                }
            }.toTypedArray<SqlParameterSource>()

        if (params.isEmpty()) return

        template.batchUpdate(
            """
            INSERT INTO ansatt_arrangor_koordinator (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra, gyldig_til)
            VALUES (:ansattId, :arrangorId, :deltakerlisteId, :gyldigFra, :gyldigTil)
            ON CONFLICT (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    private data class AnsattArrangorRad(
        val ansattId: UUID,
        val arrangorId: UUID,
    )

    private data class RolleRad(
        val ansattId: UUID,
        val arrangorId: UUID,
        val rolle: RolleDbo,
    )

    private data class VeilederRad(
        val ansattId: UUID,
        val arrangorId: UUID,
        val veileder: VeilederDeltakerDbo,
    )

    private data class KoordinatorRad(
        val ansattId: UUID,
        val arrangorId: UUID,
        val koordinator: KoordinatorsDeltakerlisteDbo,
    )
}
