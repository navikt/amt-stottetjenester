package no.nav.enhetsregister.kafka

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class KafkaOutboxMetrics(registry: MeterRegistry, jdbcTemplate: JdbcTemplate) {
    init {
        Gauge
            .builder("amt_enhetsregister_kafka_outbox_ventende", jdbcTemplate) {
                checkNotNull(it.queryForObject("SELECT count(*) FROM kafka_producer_record", Long::class.java)).toDouble()
            }.register(registry)
    }
}
