package no.nav.enhetsregister.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.env")
data class KafkaTopicProperties(
    val virksomheterTopic: String,
)
