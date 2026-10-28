package no.nav.amt.person.service.kafka.config

import java.util.Properties

interface KafkaProperties {
    fun consumer(): Properties

    fun producer(): Properties
}
