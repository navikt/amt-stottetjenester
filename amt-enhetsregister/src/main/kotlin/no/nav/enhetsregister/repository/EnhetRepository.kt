package no.nav.enhetsregister.repository

import no.nav.enhetsregister.repository.command.UpsertEnhetCmd
import no.nav.enhetsregister.repository.model.EnhetRecord
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class EnhetRepository(
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun hentEnhet(organisasjonsnummer: String): EnhetRecord? {
        val sql =
            """
            SELECT
                organisasjonsnummer,
                navn,
                overordnet_enhet
            FROM enhet
            WHERE organisasjonsnummer = :organisasjonsnummer
            LIMIT 1
            """.trimIndent()

        val parameters = MapSqlParameterSource().addValue("organisasjonsnummer", organisasjonsnummer)
        return namedJdbcTemplate.query(sql, parameters, rowMapper).firstOrNull()
    }

    fun upsertEnhet(upsertEnhetCmd: UpsertEnhetCmd) {
        val sql =
            """
            INSERT INTO enhet (
                organisasjonsnummer, 
                navn, 
                overordnet_enhet
            ) 
            VALUES (
                :organisasjonsnummer, 
                :navn, 
                :overordnetEnhet
            )
            ON CONFLICT (organisasjonsnummer) DO UPDATE SET 
                navn = EXCLUDED.navn, 
                overordnet_enhet = EXCLUDED.overordnet_enhet, 
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val parameters = MapSqlParameterSource()
            .addValue("organisasjonsnummer", upsertEnhetCmd.organisasjonsnummer)
            .addValue("navn", upsertEnhetCmd.navn)
            .addValue("overordnetEnhet", upsertEnhetCmd.overordnetEnhet)

        namedJdbcTemplate.update(sql, parameters)
    }

    private companion object {
        val rowMapper = RowMapper { rs, _ ->
            EnhetRecord(
                organisasjonsnummer = rs.getString("organisasjonsnummer"),
                navn = rs.getString("navn"),
                overordnetEnhet = rs.getString("overordnet_enhet"),
            )
        }
    }
}
