package no.nav.amt.person.service.metrics

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Repository

@Repository
class MetricRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun getCounts(): Metrics {
        val sql =
            """
            SELECT
            	(SELECT count(*) FROM person) AS antall_personer,
            	(SELECT count(*) FROM nav_bruker) AS antall_nav_brukere,
            	(SELECT count(*) FROM nav_ansatt) AS antall_nav_ansatte,
            	(SELECT count(*) FROM nav_enhet) AS antall_nav_enheter,
                (SELECT count(*) FROM person_rolle WHERE type = 'ARRANGOR_ANSATT') AS antall_arrangor_ansatte
            """.trimIndent()

        return template
            .query(sql) { rs, _ ->
                Metrics(
                    antallPersoner = rs.getInt("antall_personer"),
                    antallNavBrukere = rs.getInt("antall_nav_brukere"),
                    antallNavAnsatte = rs.getInt("antall_nav_ansatte"),
                    antallNavEnheter = rs.getInt("antall_nav_enheter"),
                    antallArrangorAnsatte = rs.getInt("antall_arrangor_ansatte"),
                )
            }.first()
    }

    fun getKafkaOutboxCount(): Int = template.jdbcTemplate
        .queryForObject<Int>("SELECT count(*) FROM kafka_producer_record")
        ?: throw IllegalStateException("Klarte ikke å hente antall ventende Kafka-meldinger")
}
