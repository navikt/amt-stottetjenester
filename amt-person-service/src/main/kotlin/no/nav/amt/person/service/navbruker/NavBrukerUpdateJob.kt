package no.nav.amt.person.service.navbruker

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.common.job.JobRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class NavBrukerUpdateJob(
    private val navBrukerRepository: NavBrukerRepository,
    private val navBrukerService: NavBrukerService,
) {
    @Scheduled(cron = "@hourly")
    @SchedulerLock(name = "navBrukerUpdater", lockAtMostFor = "60m")
    fun update() {
        JobRunner.run("oppdater_nav_brukere") { oppdaterBrukere() }
    }

    private fun oppdaterBrukere() {
        val personidenter = navBrukerRepository
            .getPersonidenter(
                offset = 0,
                limit = 10000,
                notSyncedSince = LocalDateTime.now().minusDays(14),
            ).toSet()

        navBrukerService.syncKontaktinfoBulk(personidenter)
    }
}
