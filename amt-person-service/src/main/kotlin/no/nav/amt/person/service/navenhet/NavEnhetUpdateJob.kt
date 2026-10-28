package no.nav.amt.person.service.navenhet

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.common.job.JobRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NavEnhetUpdateJob(
    private val navEnhetRepository: NavEnhetRepository,
    private val navEnhetService: NavEnhetService,
) {
    @Scheduled(cron = "@daily")
    @SchedulerLock(name = "NavEnhetUpdateJob", lockAtMostFor = "60m")
    fun update() {
        JobRunner.runAsync("oppdater_nav_enheter") { oppdaterEnheter() }
    }

    private fun oppdaterEnheter() {
        val enheter = navEnhetRepository.getAll()
        navEnhetService.oppdaterNavEnheter(enheter)
    }
}
