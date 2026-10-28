package no.nav.enhetsregister.schedule

import no.nav.common.job.JobRunner
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.enhetsregister.service.DeltaOppdateringEnhetService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DeltaOppdateringSchedule(
    private val leaderElectionClient: LeaderElectionClient,
    private val deltaOppdateringEnhetService: DeltaOppdateringEnhetService,
) {
    // Every hour at 0 minutes
    @Scheduled(cron = "0 0 * * * *")
    fun oppdaterModerenheterSchedule() {
        if (leaderElectionClient.isLeader) {
            JobRunner.run("delta_oppdater_moderenheter") { deltaOppdateringEnhetService.deltaOppdaterModerenheter() }
        }
    }

    // Every hour at 30 minutes
    @Scheduled(cron = "0 30 * * * *")
    fun oppdaterUnderenhetSchedule() {
        if (leaderElectionClient.isLeader) {
            JobRunner.run("delta_oppdater_underenheter") { deltaOppdateringEnhetService.deltaOppdaterUnderenheter() }
        }
    }
}
