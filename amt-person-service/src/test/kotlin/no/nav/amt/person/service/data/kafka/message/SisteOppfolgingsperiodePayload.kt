package no.nav.amt.person.service.data.kafka.message

data class SisteOppfolgingsperiodePayload(
    val ident: String,
    val kontor: KontorPayload?,
    val sisteEndringsType: String,
    val sluttTidspunkt: String? = null,
)

data class KontorPayload(
    val kontorId: String,
    val kontorNavn: String,
)
