package no.nav.amt.person.service.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.env")
data class KafkaTopicProperties(
    val sisteOppfolgingsperiodeTopic: String,
    val sisteTilordnetVeilederTopic: String,
    val aktorV2Topic: String,
    val skjermedePersonerTopic: String,
    val leesahTopic: String,
    val deltakerV2Topic: String,
    val oppfolgingsperiodeTopic: String,
    val innsatsgruppeTopic: String,
    // Producer topics:
    val amtNavBrukerTopic: String,
    val amtArrangorAnsattPersonaliaTopic: String,
    val amtNavAnsattPersonaliaTopic: String,
    val amtNavEnhetTopic: String,
)
