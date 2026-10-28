package no.nav.arrangor.metrics

import no.nav.arrangor.MetricsService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MetricEventListener(
    private val metricsService: MetricsService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMetricEvent(event: MetricEvent) {
        when (event.name) {
            MetricEvent.MetricName.ARRANGOR_CHANGED -> metricsService.incEndredeArrangorer(event.count)
            MetricEvent.MetricName.KOORDINATOR_ADDED -> metricsService.incLagtTilSomKoordinator(event.count)
            MetricEvent.MetricName.KOORDINATOR_REMOVED -> metricsService.incFjernetSomKoordinator(event.count)
            MetricEvent.MetricName.VEILEDER_ADDED -> metricsService.incLagtTilSomVeileder(event.count)
            MetricEvent.MetricName.VEILEDER_REMOVED -> metricsService.incFjernetSomVeileder(event.count)
            MetricEvent.MetricName.VIRKSOMHET_EVENT_CONSUMED -> metricsService.incConsumedVirksomhetEndring(event.count)
        }
    }
}
