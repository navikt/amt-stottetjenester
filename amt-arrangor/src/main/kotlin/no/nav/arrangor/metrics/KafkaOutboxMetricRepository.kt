package no.nav.arrangor.metrics

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Repository

@Repository
class KafkaOutboxMetricRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun getPendingRecordCount(): Int = jdbcTemplate.queryForObject<Int>(
        "SELECT count(*) FROM kafka_producer_record",
    ) ?: throw IllegalStateException("Klarte ikke å hente antall ventende Kafka-meldinger")
}
