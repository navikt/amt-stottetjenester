package no.nav.amt.altinn.acl.repository

import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.repository.dbo.RolleDbo
import no.nav.amt.altinn.acl.utils.DbUtils.sqlParameters
import no.nav.amt.altinn.acl.utils.getNullableZonedDateTime
import no.nav.amt.altinn.acl.utils.getZonedDateTime
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class RolleRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun createRoller(
        personId: Long,
        rolleOgOrganisasjonsnummerSett: Set<Pair<RolleType, String>>,
    ) {
        if (rolleOgOrganisasjonsnummerSett.isEmpty()) {
            return
        }

        val sql =
            """
            INSERT INTO rolle(
                person_id,
                organisasjonsnummer,
                rolle,
                valid_from
            )
            VALUES (
                :person_id,
                :organisasjonsnummer,
                :rolle,
                NOW()
            )
            """.trimIndent()

        val batchParams = rolleOgOrganisasjonsnummerSett
            .map { (rolleType, organisasjonsnummer) ->
                sqlParameters(
                    "person_id" to personId,
                    "rolle" to rolleType.toString(),
                    "organisasjonsnummer" to organisasjonsnummer,
                )
            }.toTypedArray()

        template.batchUpdate(sql, batchParams)
    }

    fun fjernRoller(rolleIder: Set<Long>) {
        val sql =
            """
            UPDATE rolle
            SET valid_to = NOW()
            WHERE id IN (:rolleIder)
            """.trimIndent()

        template.update(sql, sqlParameters("rolleIder" to rolleIder))
    }

    fun hentGyldigeRollerForPerson(norskIdent: String): List<RolleDbo> {
        val sql =
            """
            SELECT 
                rolle.id,
                rolle.person_id,       
                rolle.organisasjonsnummer,                         
                rolle.rolle,
                rolle.valid_from,
                rolle.valid_to
            FROM 
                rolle
                JOIN person ON person.id = rolle.person_id
            WHERE 
                person.norsk_ident = :norsk_ident
                AND rolle.valid_to IS NULL
            """.trimIndent()

        return template.query(sql, sqlParameters("norsk_ident" to norskIdent), rowMapper)
    }

    private val rowMapper = RowMapper { rs, _ ->
        RolleDbo(
            id = rs.getLong("id"),
            personId = rs.getLong("person_id"),
            organisasjonsnummer = rs.getString("organisasjonsnummer"),
            rolleType = RolleType.valueOf(rs.getString("rolle")),
            validFrom = rs.getZonedDateTime("valid_from"),
            validTo = rs.getNullableZonedDateTime("valid_to"),
        )
    }
}
