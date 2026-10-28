package no.nav.amt.person.service.data

import no.nav.amt.lib.utils.toPGObject
import no.nav.amt.person.service.data.TestData.navGrunerlokka
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.dbo.PersonidentDbo
import no.nav.amt.person.service.person.model.Rolle
import no.nav.amt.person.service.utils.sqlParameters
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Component
class TestDataRepository(
    private val template: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun insertPerson(person: PersonDbo) {
        val sql =
            """
            insert into person(
            	id,
            	personident,
            	fornavn,
            	mellomnavn,
            	etternavn,
            	created_at,
            	modified_at
            ) values (
            	:id,
            	:personident,
            	:fornavn,
            	:mellomnavn,
            	:etternavn,
            	:createdAt,
            	:modifiedAt
            )
            """.trimIndent()

        template.update(
            sql,
            sqlParameters(
                "id" to person.id,
                "personident" to person.personident,
                "fornavn" to person.fornavn,
                "mellomnavn" to person.mellomnavn,
                "etternavn" to person.etternavn,
                "createdAt" to person.createdAt,
                "modifiedAt" to person.modifiedAt,
            ),
        )

        insertPersonidenter(
            listOf(TestData.lagPersonident(person.personident, person.id)),
        )
    }

    fun insertPersonidenter(identer: List<PersonidentDbo>) {
        val sql =
            """
            insert into personident(
            	ident,
            	person_id,
            	type,
            	historisk
            ) values (
            	:ident,
            	:personId,
            	:type,
            	:historisk
            )
            """.trimIndent()

        val parameters =
            identer.map {
                sqlParameters(
                    "ident" to it.ident,
                    "personId" to it.personId,
                    "historisk" to it.historisk,
                    "type" to it.type.name,
                )
            }
        template.batchUpdate(sql, parameters.toTypedArray())
    }

    final fun insertNavGrunerlokka() = insertNavEnhet(
        NavEnhetDbo(
            navGrunerlokka.id,
            navGrunerlokka.enhetId,
            navGrunerlokka.navn,
            LocalDateTime.now(),
            LocalDateTime.now(),
        ),
    )

    fun insertNavEnhet(enhet: NavEnhetDbo) {
        val sql =
            """
            INSERT INTO nav_enhet(id, nav_enhet_id, navn, created_at, modified_at)
            VALUES (:id, :enhetId, :navn, :createdAt, :modifiedAt)
            ON CONFLICT (nav_enhet_id) DO update set id = :id, nav_enhet_id = :enhetId, navn = :navn, created_at = :createdAt, modified_at = :modifiedAt
            """.trimIndent()

        val parameters =
            sqlParameters(
                "id" to enhet.id,
                "enhetId" to enhet.enhetId,
                "navn" to enhet.navn,
                "createdAt" to enhet.createdAt,
                "modifiedAt" to enhet.modifiedAt,
            )

        template.update(sql, parameters)
    }

    fun insertNavAnsatt(ansatt: NavAnsattDbo) {
        insertNavGrunerlokka()
        val sql =
            """
            insert into nav_ansatt(
            	id,
            	nav_ident,
            	navn,
            	telefon,
            	epost,
            	nav_enhet_id,
            	created_at,
            	modified_at
            ) values (
            	:id,
            	:navIdent,
            	:navn,
            	:telefon,
            	:epost,
            	:nav_enhet_id,
            	:createdAt,
            	:modifiedAt
            )
            """.trimIndent()

        val parameters =
            sqlParameters(
                "id" to ansatt.id,
                "navIdent" to ansatt.navIdent,
                "navn" to ansatt.navn,
                "telefon" to ansatt.telefon,
                "epost" to ansatt.epost,
                "nav_enhet_id" to ansatt.navEnhetId,
                "createdAt" to ansatt.createdAt,
                "modifiedAt" to ansatt.modifiedAt,
            )

        template.update(sql, parameters)
    }

    fun insertRolle(
        personId: UUID,
        rolle: Rolle,
    ) {
        val sql =
            """
            insert into person_rolle(id, person_id, type)
            values(:id, :personId, :rolle)
            """.trimIndent()

        val parameters =
            sqlParameters(
                "id" to UUID.randomUUID(),
                "personId" to personId,
                "rolle" to rolle.name,
            )

        template.update(sql, parameters)
    }

    fun insertNavBruker(bruker: NavBrukerDbo) {
        try {
            insertPerson(bruker.person)
        } catch (_: DuplicateKeyException) {
            log.warn("Person med id ${bruker.person.id} er allerede opprettet")
        }

        bruker.navVeileder?.let {
            try {
                insertNavAnsatt(bruker.navVeileder)
            } catch (_: DuplicateKeyException) {
                log.warn("Nav ansatt med id ${bruker.navVeileder.id} er allerede opprettet")
            }
        }

        bruker.navEnhet?.let {
            try {
                insertNavEnhet(bruker.navEnhet)
            } catch (_: DuplicateKeyException) {
                log.warn("Nav enhet med id ${bruker.navEnhet.id} er allerede opprettet")
            }
        }

        insertRolle(bruker.person.id, Rolle.NAV_BRUKER)

        val sql =
            """
            insert into nav_bruker(
            	id,
            	person_id,
            	nav_veileder_id,
            	nav_enhet_id,
            	telefon,
            	epost,
            	er_skjermet,
            	siste_krr_sync,
            	adresse,
            	adressebeskyttelse,
            	oppfolgingsperioder,
            	innsatsgruppe,
            	created_at,
            	modified_at
            ) values (
            	:id,
            	:personId,
            	:navVeilederId,
            	:navEnhetId,
            	:telefon,
            	:epost,
            	:erSkjermet,
            	:sisteKrrSync,
            	:adresse,
            	:adressebeskyttelse,
            	:oppfolgingsperioder,
            	:innsatsgruppe,
            	:createdAt,
            	:modifiedAt
            )
            """.trimIndent()

        val parameters =
            sqlParameters(
                "id" to bruker.id,
                "personId" to bruker.person.id,
                "navVeilederId" to bruker.navVeileder?.id,
                "navEnhetId" to bruker.navEnhet?.id,
                "telefon" to bruker.telefon,
                "epost" to bruker.epost,
                "erSkjermet" to bruker.erSkjermet,
                "sisteKrrSync" to bruker.sisteKrrSync,
                "adresse" to bruker.adresse?.let { objectMapper.toPGObject(it) },
                "adressebeskyttelse" to bruker.adressebeskyttelse?.name,
                "oppfolgingsperioder" to objectMapper.toPGObject(bruker.oppfolgingsperioder),
                "innsatsgruppe" to bruker.innsatsgruppe?.name,
                "createdAt" to bruker.createdAt,
                "modifiedAt" to bruker.modifiedAt,
            )

        template.update(sql, parameters)
    }
}
