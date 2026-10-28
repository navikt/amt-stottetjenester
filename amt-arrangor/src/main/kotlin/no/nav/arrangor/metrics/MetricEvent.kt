package no.nav.arrangor.metrics

data class MetricEvent(
    val name: MetricName,
    val count: Int = 1,
) {
    enum class MetricName {
        ARRANGOR_CHANGED,
        KOORDINATOR_ADDED,
        KOORDINATOR_REMOVED,
        VEILEDER_ADDED,
        VEILEDER_REMOVED,
        VIRKSOMHET_EVENT_CONSUMED,
    }
}
