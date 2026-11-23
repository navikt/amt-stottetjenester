package no.nav.enhetsregister.kafka

import no.nav.amt.lib.spring.kafka.outbox.KafkaOutboxStorageConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@Import(KafkaOutboxStorageConfiguration::class)
class KafkaOutboxStorageConfig
