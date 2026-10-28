package no.nav.amt.person.service

import no.nav.amt.person.service.kafka.config.KafkaTopicProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(KafkaTopicProperties::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
