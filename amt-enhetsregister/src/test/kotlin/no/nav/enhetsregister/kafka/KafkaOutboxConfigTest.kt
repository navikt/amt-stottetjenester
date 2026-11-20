package no.nav.enhetsregister.kafka

import io.mockk.mockk
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.JdbcTemplate
import java.util.function.Supplier

class KafkaOutboxConfigTest {
    @Test
    fun `disabled Kafka retains storage without producer processor or lifecycle`() {
        ApplicationContextRunner()
            .withUserConfiguration(
                KafkaConfig::class.java,
                KafkaOutboxStorageConfig::class.java,
                KafkaOutboxProcessorConfig::class.java,
                KafkaOutboxLifecycle::class.java,
            )
            .withBean(JdbcTemplate::class.java, Supplier { mockk<JdbcTemplate>(relaxed = true) })
            .withPropertyValues("kafka.enabled=false", "app.env.virksomheterTopic=test-topic")
            .run {
                assertThat(it).hasNotFailed()
                assertThat(it).hasSingleBean(KafkaProducerRecordStorage::class.java)
                assertThat(it).hasSingleBean(PostgresJdbcTemplateProducerRepository::class.java)
                assertThat(it).doesNotHaveBean(KafkaProducerClient::class.java)
                assertThat(it).doesNotHaveBean(KafkaProducerRecordProcessor::class.java)
                assertThat(it).doesNotHaveBean(KafkaOutboxLifecycle::class.java)
            }
    }
}
