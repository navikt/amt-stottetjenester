package no.nav.arrangor.configuration

import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import no.nav.arrangor.ansatt.AnsattService
import no.nav.common.job.JobRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class Jobs(
    private val leaderElection: LeaderElectionClient,
    private val ansattService: AnsattService,
) {
    @Scheduled(cron = "@hourly")
    suspend fun updateRoller() {
        if (leaderElection.isLeader()) {
            JobRunner.run("Oppdater roller") { ansattService.oppdaterAnsattesRoller() }
        }
    }
}
