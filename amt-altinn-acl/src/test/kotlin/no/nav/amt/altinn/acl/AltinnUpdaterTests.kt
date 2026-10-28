package no.nav.amt.altinn.acl

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.domain.RolleType.KOORDINATOR
import no.nav.amt.altinn.acl.domain.RolleType.VEILEDER
import no.nav.amt.altinn.acl.domain.RollerIOrganisasjon
import no.nav.amt.altinn.acl.jobs.AltinnUpdater
import no.nav.amt.altinn.acl.repository.PersonRepository
import no.nav.amt.altinn.acl.service.RolleService
import no.nav.amt.altinn.acl.testutil.IntegrationTest
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class AltinnUpdaterTests(
    private val personRepository: PersonRepository,
    private val rolleService: RolleService,
    private val altinnUpdater: AltinnUpdater,
    @MockkBean private val leaderElection: LeaderElectionClient,
) : IntegrationTest() {
    @BeforeEach
    fun setup() {
        coEvery { leaderElection.isLeader() } returns true
    }

    @Test
    fun `update - utdatert bruker - skal synkronisere bruker`() = runTest {
        val organisasjonsnummer = "2131"
        val personligIdent = Random.nextLong().toString()

        personRepository.createAndSetSynchronized(personligIdent, Instant.EPOCH.atZone(ZoneOffset.UTC))

        every {
            altinnClient.hentRoller(personligIdent, RolleType.entries)
        } returns mapOf(KOORDINATOR to listOf(organisasjonsnummer), VEILEDER to emptyList())

        altinnUpdater.update()

        val oppdaterteRettigheter = rolleService.getRollerForPerson(personligIdent)
        hasRolle(oppdaterteRettigheter, organisasjonsnummer, KOORDINATOR) shouldBe true
    }

    private fun hasRolle(
        list: List<RollerIOrganisasjon>,
        organizationNumber: String,
        rolle: RolleType,
    ): Boolean = list
        .find { it.organisasjonsnummer == organizationNumber }
        ?.roller
        ?.find { it.rolleType == rolle } != null
}
