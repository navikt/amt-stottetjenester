package no.nav.amt.altinn.acl.repository

import io.kotest.matchers.shouldBe
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.repository.dbo.PersonDbo
import no.nav.amt.altinn.acl.repository.dbo.RolleDbo
import no.nav.amt.altinn.acl.testutil.RepositoryTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@SpringBootTest(classes = [RolleRepository::class, PersonRepository::class])
class RolleRepositoryTest(
    private val personRepository: PersonRepository,
    private val rolleRepository: RolleRepository,
    private val jdbcTemplate: JdbcTemplate,
) : RepositoryTestBase() {
    @Nested
    inner class OpprettRoller {
        @Test
        fun `oppretter flere roller for personen`() {
            // Arrange
            val person = opprettPerson()
            val roller = setOf(
                RolleType.VEILEDER to nyttOrganisasjonsnummer(),
                RolleType.KOORDINATOR to nyttOrganisasjonsnummer(),
            )

            // Act
            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = roller,
            )

            // Assert
            val opprettedeRoller = jdbcTemplate.query(
                "SELECT rolle, organisasjonsnummer FROM rolle WHERE person_id = ? AND valid_to IS NULL",
                { rs, _ ->
                    RolleType.valueOf(rs.getString("rolle")) to rs.getString("organisasjonsnummer")
                },
                person.id,
            )

            opprettedeRoller.size shouldBe roller.size
            opprettedeRoller.toSet() shouldBe roller.toSet()
        }

        @Test
        fun `oppretter ingen roller når lista er tom`() {
            // Arrange
            val person = opprettPerson()

            // Act
            rolleRepository.createRoller(
                personId = person.id,
                rolleOgOrganisasjonsnummerSett = emptySet(),
            )

            // Assert
            jdbcTemplate.query(
                "SELECT id FROM rolle WHERE person_id = ?",
                { rs, _ -> rs.getLong("id") },
                person.id,
            ) shouldBe emptyList()
        }
    }

    @Nested
    inner class FjernRoller {
        @Test
        fun `ugyldiggjør bare roller med oppgitte ider`() {
            // Arrange
            val person = opprettPerson()
            val roller = List(3) { opprettRolle(person) }
            val rolleIderSomSkalFjernes = setOf(roller[0].id, roller[1].id)

            // Act
            rolleRepository.fjernRoller(rolleIderSomSkalFjernes)

            // Assert
            val gyldighetPerRolleId = jdbcTemplate
                .query(
                    "SELECT id, valid_to IS NULL AS is_valid FROM rolle WHERE person_id = ?",
                    { rs, _ -> rs.getLong("id") to rs.getBoolean("is_valid") },
                    person.id,
                ).toMap()

            gyldighetPerRolleId shouldBe mapOf(
                roller[0].id to false,
                roller[1].id to false,
                roller[2].id to true,
            )
        }
    }

    @Nested
    inner class HentGyldigeRollerForPerson {
        @Test
        fun `henter bare gyldige roller for personen`() {
            // Arrange
            val person = opprettPerson()
            val gyldigRolle = opprettRolle(person, RolleType.VEILEDER)
            val ugyldigRolle = opprettRolle(person, RolleType.KOORDINATOR)
            rolleRepository.fjernRoller(setOf(ugyldigRolle.id))
            opprettRolle(opprettPerson(), RolleType.KOORDINATOR)

            // Act
            val roller = rolleRepository.hentGyldigeRollerForPerson(person.norskIdent)

            // Assert
            roller.map { it.id } shouldBe listOf(gyldigRolle.id)
        }
    }

    private fun opprettPerson(): PersonDbo = personRepository.createAndSetSynchronized("ident-${UUID.randomUUID()}")

    private fun opprettRolle(
        person: PersonDbo,
        rolleType: RolleType = RolleType.VEILEDER,
    ): RolleDbo {
        val organisasjonsnummer = nyttOrganisasjonsnummer()
        rolleRepository.createRoller(
            personId = person.id,
            setOf(Pair(rolleType, organisasjonsnummer)),
        )

        return rolleRepository
            .hentGyldigeRollerForPerson(person.norskIdent)
            .single { it.rolleType == rolleType && it.organisasjonsnummer == organisasjonsnummer }
    }

    private fun nyttOrganisasjonsnummer(): String = UUID.randomUUID().toString()
}
