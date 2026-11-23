package no.nav.enhetsregister.kafka

import io.micrometer.core.instrument.MeterRegistry
import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxBacklogGauge
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class KafkaOutboxMetrics(registry: MeterRegistry, jdbcTemplate: JdbcTemplate) {
    init {
        KafkaOutboxBacklogGauge(registry, jdbcTemplate, "amt_enhetsregister_kafka_outbox_ventende")
    }
}
