package no.nav.amt.person.service.metrics

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.common.job.JobRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MetricJob(
    private val metricService: MetricService,
) {
    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    @SchedulerLock(name = "MetricJob", lockAtMostFor = "30m")
    fun oppdaterMetrikker() {
        JobRunner.run("oppdater_metrikker", metricService::oppdaterMetrikker)
    }
}
