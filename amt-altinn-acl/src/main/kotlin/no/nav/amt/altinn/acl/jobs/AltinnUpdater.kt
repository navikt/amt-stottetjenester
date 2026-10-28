package no.nav.amt.altinn.acl.jobs

import no.nav.amt.altinn.acl.service.RolleService
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import no.nav.common.job.JobRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AltinnUpdater(
    private val rolleService: RolleService,
    private val leaderElection: LeaderElectionClient,
) {
    @Scheduled(cron = "@hourly")
    suspend fun update() {
        if (leaderElection.isLeader()) {
            JobRunner.run("synkroniser_altinn_rettigheter") {
                rolleService.synchronizeUsers()
            }
        }
    }
}
