package no.nav.amt.person.service.navansatt

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.common.job.JobRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NavAnsattUpdateJob(
    private val navAnsattUpdater: NavAnsattUpdater,
) {
    @Scheduled(cron = "@midnight")
    @SchedulerLock(name = "navAnsattUpdater", lockAtMostFor = "30m", lockAtLeastFor = "15m")
    fun update() {
        JobRunner.run("oppdater_alle_nav_ansatte", navAnsattUpdater::oppdaterAlle)
    }
}
