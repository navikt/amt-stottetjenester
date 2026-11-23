package no.nav.arrangor

import io.micrometer.core.instrument.MeterRegistry
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxBacklogGauge
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class MetricsService(
    registry: MeterRegistry,
    jdbcTemplate: JdbcTemplate,
) {
    private val publishedArrangor = registry.counter("amt_arrangor_publiserte_arrangorer")
    private val endredeArrangorer = registry.counter("amt_arrangor_endrede_arrangorer")

    private val lagtTilSomKoordinator = registry.counter("amt_arrangor_lagt_til_som_koordinator")
    private val fjernetSomKoordinator = registry.counter("amt_arrangor_fjernet_til_som_koordinator")

    private val lagtTilSomVeileder = registry.counter("amt_arrangor_lagt_til_som_veilederr")
    private val fjernetSomVeileder = registry.counter("amt_arrangor_fjernet_til_som_veilederr")

    private val publishedAnsatte = registry.counter("amt_arrangor_publiserte_ansatte")

    private val consumedVirksomhetEndring = registry.counter("amt_arrangor_consumed_virksomhet")
    private val consumerFailed = registry.counter("amt_arrangor_consume_failed")

    init {
        KafkaOutboxBacklogGauge(registry, jdbcTemplate, "amt_arrangor_kafka_outbox_ventende")
    }

    fun incEndredeArrangorer(count: Int = 1) = endredeArrangorer.increment(count.toDouble())

    fun incPubliserteArrangorer(count: Int = 1) = publishedArrangor.increment(count.toDouble())

    fun incLagtTilSomKoordinator(count: Int = 1) = lagtTilSomKoordinator.increment(count.toDouble())

    fun incFjernetSomKoordinator(count: Int = 1) = fjernetSomKoordinator.increment(count.toDouble())

    fun incLagtTilSomVeileder(count: Int = 1) = lagtTilSomVeileder.increment(count.toDouble())

    fun incFjernetSomVeileder(count: Int = 1) = fjernetSomVeileder.increment(count.toDouble())

    fun incPubliserteAnsatte(count: Int = 1) = publishedAnsatte.increment(count.toDouble())

    fun incConsumedVirksomhetEndring(count: Int = 1) = consumedVirksomhetEndring.increment(count.toDouble())

    fun incConsumerFailed(count: Int = 1) = consumerFailed.increment(count.toDouble())
}
