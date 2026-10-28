package no.nav.amt.person.service.data.kafka.message

import java.time.ZonedDateTime

data class TildeltVeilederMsg(
    val aktorId: String,
    val veilederId: String,
    val tilordnet: ZonedDateTime,
)
