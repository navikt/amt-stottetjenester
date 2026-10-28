package no.nav.amt.person.service.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class MetricService(
    private val repository: MetricRepository,
    private val meterRegistry: MeterRegistry,
) {
    private val antallPersoner = gauge("amt_person_antall_personer")
    private val antallNavBruker = gauge("amt_person_antall_nav_brukere")
    private val antallNavAnsatte = gauge("amt_person_antall_nav_ansatte")
    private val antallNavEnheter = gauge("amt_person_antall_nav_enheter")
    private val antallArrangorAnsatte = gauge("amt_person_antall_arrangor_ansatte")

    init {
        // Leses ved hver Prometheus-scrape, slik at alle poder rapporterer fersk verdi uten en distribuert scheduler-lås.
        Gauge
            .builder(
                "amt_person_kafka_outbox_ventende",
                repository,
            ) {
                it.getKafkaOutboxCount().toDouble()
            }.register(meterRegistry)
    }

    fun oppdaterMetrikker() {
        val counts = repository.getCounts()

        antallPersoner.set(counts.antallPersoner)
        antallNavBruker.set(counts.antallNavBrukere)
        antallNavAnsatte.set(counts.antallNavAnsatte)
        antallNavEnheter.set(counts.antallNavEnheter)
        antallArrangorAnsatte.set(counts.antallArrangorAnsatte)
    }

    private fun gauge(name: String): AtomicInteger = meterRegistry.gauge(name, AtomicInteger(0))
}
