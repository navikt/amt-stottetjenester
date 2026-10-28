package no.nav.amt.person.service.kafka.consumer.dto

import java.time.Instant

data class SisteOppfolgingsperiodePayload(
    val ident: String,
    val kontor: KontorPayload?,
    val sisteEndringsType: String,
    val sluttTidspunkt: Instant?,
)

data class KontorPayload(
    val kontorId: String,
    val kontorNavn: String,
)
