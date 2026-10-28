package no.nav.amt.person.service.navbruker

import no.nav.amt.lib.utils.toPGObject
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.model.Adresse
import no.nav.amt.person.service.utils.getNullableUUID
import no.nav.amt.person.service.utils.getUUID
import no.nav.amt.person.service.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Repository
class NavBrukerRepository(
    private val template: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun get(id: UUID): NavBrukerDbo = template
        .query(
            selectNavBrukerQuery("WHERE nav_bruker.id = :id"),
            sqlParameters("id" to id),
            rowMapper,
        ).first()

    fun get(personident: String): NavBrukerDbo? = template
        .query(
            selectNavBrukerQuery(
                """
                LEFT JOIN personident ON nav_bruker.person_id = personident.person_id
                WHERE personident.ident = :personident
                """.trimIndent(),
            ),
            sqlParameters("personident" to personident),
            rowMapper,
        ).firstOrNull()

    fun getByPersonId(personId: UUID): NavBrukerDbo? = template
        .query(
            selectNavBrukerQuery("WHERE person_id = :personId"),
            sqlParameters("personId" to personId),
            rowMapper,
        ).firstOrNull()

    fun getAllUtenAdresse(
        limit: Int,
        modifiedBefore: LocalDateTime,
        lastId: UUID?,
    ): List<NavBrukerDbo> {
        val andLastId = lastId?.let { "AND nav_bruker.id > :last_id" } ?: ""

        return template.query(
            selectNavBrukerQuery(
                """
                WHERE
                	nav_bruker.adresse IS NULL
                	AND nav_bruker.adressebeskyttelse IS NULL
                	AND nav_bruker.modified_at < :modified_before $andLastId
                ORDER BY nav_bruker.id
                LIMIT :limit
                """.trimIndent(),
            ),
            sqlParameters(
                "modified_before" to modifiedBefore,
                "last_id" to lastId,
                "limit" to limit,
            ),
            rowMapper,
        )
    }

    fun getAllNavBrukere(
        offset: Int,
        limit: Int,
    ): List<NavBrukerDbo> = template.query(
        selectNavBrukerQuery("ORDER BY nav_bruker.created_at OFFSET :offset LIMIT :limit"),
        sqlParameters("offset" to offset, "limit" to limit),
        rowMapper,
    )

    fun getAllNavBrukere(
        limit: Int,
        modifiedBefore: LocalDate,
        lastId: UUID?,
    ): List<NavBrukerDbo> {
        val andLastId = lastId?.let { "AND nav_bruker.id > :last_id" } ?: ""

        return template.query(
            selectNavBrukerQuery(
                """
                WHERE nav_bruker.modified_at < :modified_before $andLastId
                ORDER BY nav_bruker.id
                LIMIT :limit
                """.trimIndent(),
            ),
            sqlParameters(
                "modified_before" to modifiedBefore,
                "limit" to limit,
                "last_id" to lastId,
            ),
            rowMapper,
        )
    }

    fun getPersonidenter(
        offset: Int,
        limit: Int,
        notSyncedSince: LocalDateTime? = null,
    ): List<String> {
        val sql =
            """
            SELECT DISTINCT ON (person.personident) person.personident
            FROM
            	nav_bruker
            	JOIN person ON nav_bruker.person_id = person.id
            WHERE
            	nav_bruker.siste_krr_sync IS NULL
            	${notSyncedSince?.let { "OR nav_bruker.siste_krr_sync < :notSyncedSince" } ?: ""}
            ORDER BY
            	person.personident,
            	nav_bruker.siste_krr_sync NULLS FIRST,
            	nav_bruker.modified_at
            OFFSET :offset
            LIMIT :limit
            """.trimIndent()

        val parameters = sqlParameters(
            "offset" to offset,
            "limit" to limit,
            "notSyncedSince" to notSyncedSince,
        )

        return template.query(sql, parameters) { rs, _ -> rs.getString("personident") }
    }

    fun getPersonidenterMedManglendeKontaktinfo(
        sistePersonident: String?,
        limit: Int,
    ): List<String> {
        val sql =
            """
            SELECT DISTINCT person.personident
            FROM
            	nav_bruker
            	JOIN person ON nav_bruker.person_id = person.id
            WHERE ${sistePersonident?.let { "personident < :siste_personident AND" } ?: ""}
            (nav_bruker.telefon IS NULL OR nav_bruker.epost IS NULL)
            ORDER BY personident DESC
            LIMIT :limit
            """.trimIndent()

        val parameters = sqlParameters(
            "siste_personident" to sistePersonident,
            "limit" to limit,
        )

        return template.query(sql, parameters) { rs, _ -> rs.getString("personident") }
    }

    fun upsert(navBruker: NavBrukerDbo) {
        val sql =
            """
            INSERT INTO nav_bruker (
            	id,
            	person_id,
            	nav_veileder_id,
            	nav_enhet_id,
            	telefon,
            	epost,
            	er_skjermet,
            	adresse,
            	siste_krr_sync,
            	adressebeskyttelse,
            	oppfolgingsperioder,
            	innsatsgruppe
            )
            VALUES (
            	:id,
            	:personId,
            	:navVeilederId,
            	:navEnhetId,
            	:telefon,
            	:epost,
            	:erSkjermet,
            	:adresse,
            	:sisteKrrSync,
            	:adressebeskyttelse,
            	:oppfolgingsperioder,
            	:innsatsgruppe
            )
            ON CONFLICT (person_id) DO UPDATE SET
            	nav_veileder_id = :navVeilederId,
            	nav_enhet_id = :navEnhetId,
            	telefon = :telefon,
            	epost = :epost,
            	er_skjermet = :erSkjermet,
            	adresse = :adresse,
            	siste_krr_sync = :sisteKrrSync,
            	adressebeskyttelse = :adressebeskyttelse,
            	oppfolgingsperioder = :oppfolgingsperioder,
            	innsatsgruppe = :innsatsgruppe,
            	modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val parameters = sqlParameters(
            "id" to navBruker.id,
            "personId" to navBruker.person.id,
            "navVeilederId" to navBruker.navVeileder?.id,
            "navEnhetId" to navBruker.navEnhet?.id,
            "telefon" to navBruker.telefon,
            "epost" to navBruker.epost,
            "erSkjermet" to navBruker.erSkjermet,
            "adresse" to navBruker.adresse?.let { objectMapper.toPGObject(it) },
            "sisteKrrSync" to navBruker.sisteKrrSync,
            "adressebeskyttelse" to navBruker.adressebeskyttelse?.name,
            "oppfolgingsperioder" to objectMapper.toPGObject(navBruker.oppfolgingsperioder),
            "innsatsgruppe" to navBruker.innsatsgruppe?.name,
        )

        template.update(sql, parameters)
    }

    fun finnBrukerId(personident: String): UUID? {
        val sql =
            """
            SELECT nb.id AS "nav_bruker.id"
            FROM
            	nav_bruker nb
            	JOIN person p ON nb.person_id = p.id
            	JOIN personident ident ON p.id = ident.person_id
            WHERE ident.ident = :personident
            """.trimIndent()

        val parameters = sqlParameters("personident" to personident)

        return template
            .query(sql, parameters) { rs, _ -> rs.getNullableUUID("nav_bruker.id") }
            .firstOrNull()
    }

    private val rowMapper = RowMapper { rs, _ ->
        NavBrukerDbo(
            id = rs.getUUID("nav_bruker.id"),
            person = PersonDbo(
                id = rs.getUUID("nav_bruker.person_id"),
                personident = rs.getString("person.personident"),
                erFalskIdentitet = rs.getBoolean("person.er_falsk_identitet"),
                fornavn = rs.getString("person.fornavn"),
                mellomnavn = rs.getString("person.mellomnavn"),
                etternavn = rs.getString("person.etternavn"),
                createdAt = rs.getTimestamp("person.created_at").toLocalDateTime(),
                modifiedAt = rs.getTimestamp("person.modified_at").toLocalDateTime(),
            ),
            navVeileder = rs
                .getNullableUUID("nav_bruker.nav_veileder_id")
                ?.let {
                    NavAnsattDbo(
                        id = rs.getUUID("nav_bruker.nav_veileder_id"),
                        navIdent = rs.getString("nav_ansatt.nav_ident"),
                        navn = rs.getString("nav_ansatt.navn"),
                        telefon = rs.getString("nav_ansatt.telefon"),
                        epost = rs.getString("nav_ansatt.epost"),
                        navEnhetId = rs.getNullableUUID("nav_ansatt.nav_enhet_id"),
                        createdAt = rs.getTimestamp("nav_ansatt.created_at").toLocalDateTime(),
                        modifiedAt = rs.getTimestamp("nav_ansatt.modified_at").toLocalDateTime(),
                    )
                },
            navEnhet = rs
                .getNullableUUID("nav_bruker.nav_enhet_id")
                ?.let {
                    NavEnhetDbo(
                        id = rs.getUUID("nav_bruker.nav_enhet_id"),
                        enhetId = rs.getString("nav_enhet.nav_enhet_id"),
                        navn = rs.getString("nav_enhet.navn"),
                        createdAt = rs.getTimestamp("nav_enhet.created_at").toLocalDateTime(),
                        modifiedAt = rs.getTimestamp("nav_enhet.modified_at").toLocalDateTime(),
                    )
                },
            telefon = rs.getString("nav_bruker.telefon"),
            epost = rs.getString("nav_bruker.epost"),
            erSkjermet = rs.getBoolean("nav_bruker.er_skjermet"),
            adresse = rs
                .getString("nav_bruker.adresse")
                ?.let {
                    objectMapper.readValue<Adresse>(it)
                },
            sisteKrrSync = rs.getTimestamp("siste_krr_sync")?.toLocalDateTime(),
            createdAt = rs.getTimestamp("nav_bruker.created_at").toLocalDateTime(),
            modifiedAt = rs.getTimestamp("nav_bruker.modified_at").toLocalDateTime(),
            adressebeskyttelse = rs
                .getString("nav_bruker.adressebeskyttelse")
                ?.let { Adressebeskyttelse.valueOf(it) },
            oppfolgingsperioder = rs
                .getString("nav_bruker.oppfolgingsperioder")
                ?.let { objectMapper.readValue<List<Oppfolgingsperiode>>(it) } ?: emptyList(),
            innsatsgruppe = rs
                .getString("nav_bruker.innsatsgruppe")
                ?.let { InnsatsgruppeV1.valueOf(it) },
        )
    }

    companion object {
        private fun selectNavBrukerQuery(where: String): String =
            """
            SELECT
            	nav_bruker.id AS "nav_bruker.id",
            	nav_bruker.person_id AS "nav_bruker.person_id",
            	nav_bruker.nav_veileder_id AS "nav_bruker.nav_veileder_id",
            	nav_bruker.nav_enhet_id AS "nav_bruker.nav_enhet_id",
            	nav_bruker.telefon AS "nav_bruker.telefon",
            	nav_bruker.epost AS "nav_bruker.epost",
            	nav_bruker.er_skjermet AS "nav_bruker.er_skjermet",
            	nav_bruker.adresse AS "nav_bruker.adresse",
            	nav_bruker.adressebeskyttelse AS "nav_bruker.adressebeskyttelse",
            	nav_bruker.oppfolgingsperioder AS "nav_bruker.oppfolgingsperioder",
            	nav_bruker.innsatsgruppe AS "nav_bruker.innsatsgruppe",
            	nav_bruker.siste_krr_sync,
            	nav_bruker.created_at AS "nav_bruker.created_at",
            	nav_bruker.modified_at AS "nav_bruker.modified_at",
            	person.personident AS "person.personident",
            	person.er_falsk_identitet AS "person.er_falsk_identitet",
            	person.fornavn AS "person.fornavn",
            	person.mellomnavn AS "person.mellomnavn",
            	person.etternavn AS "person.etternavn",
            	person.created_at AS "person.created_at",
            	person.modified_at AS "person.modified_at",
            	nav_ansatt.nav_ident AS "nav_ansatt.nav_ident",
            	nav_ansatt.navn AS "nav_ansatt.navn",
            	nav_ansatt.telefon AS "nav_ansatt.telefon",
            	nav_ansatt.epost AS "nav_ansatt.epost",
            	nav_ansatt.nav_enhet_id AS "nav_ansatt.nav_enhet_id",
            	nav_ansatt.created_at AS "nav_ansatt.created_at",
            	nav_ansatt.modified_at AS "nav_ansatt.modified_at",
            	nav_enhet.nav_enhet_id AS "nav_enhet.nav_enhet_id",
            	nav_enhet.navn AS "nav_enhet.navn",
            	nav_enhet.created_at AS "nav_enhet.created_at",
            	nav_enhet.modified_at AS "nav_enhet.modified_at"
            FROM
            	nav_bruker
            	LEFT JOIN person ON nav_bruker.person_id = person.id
            	LEFT JOIN nav_ansatt ON nav_bruker.nav_veileder_id = nav_ansatt.id
            	LEFT JOIN nav_enhet ON nav_bruker.nav_enhet_id = nav_enhet.id
            $where
            """.trimIndent()
    }
}
