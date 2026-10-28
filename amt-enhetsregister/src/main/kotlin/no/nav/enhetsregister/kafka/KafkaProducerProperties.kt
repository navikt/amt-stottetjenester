package no.nav.enhetsregister.kafka

import java.util.Properties

interface KafkaProducerProperties {
    fun producer(): Properties
}
