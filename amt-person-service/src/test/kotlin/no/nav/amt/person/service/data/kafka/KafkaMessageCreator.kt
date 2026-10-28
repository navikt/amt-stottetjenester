package no.nav.amt.person.service.data.kafka

import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.data.kafka.message.KontorPayload
import no.nav.amt.person.service.data.kafka.message.SisteOppfolgingsperiodePayload
import no.nav.amt.person.service.data.kafka.message.TildeltVeilederMsg
import no.nav.amt.person.service.kafka.consumer.OpplysningsType
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.adressebeskyttelse.Adressebeskyttelse
import no.nav.person.pdl.leesah.adressebeskyttelse.Gradering
import no.nav.person.pdl.leesah.navn.Navn
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

object KafkaMessageCreator {
    fun lagSisteOppfolgingsperiodeMsg(
        ident: String = TestData.randomIdent(),
        kontor: KontorPayload? = KontorPayload(
            kontorId = TestData.randomEnhetId(),
            kontorNavn = "NAV ${TestData.randomEnhetId()}",
        ),
        sisteEndringsType: String = "ENDRET_KONTOR",
    ) = SisteOppfolgingsperiodePayload(
        ident = ident,
        kontor = kontor,
        sisteEndringsType = sisteEndringsType,
    )

    fun lagTildeltVeilederMsg(
        aktorId: String = TestData.randomIdent(),
        veilederId: String = TestData.randomNavIdent(),
        tilordnet: ZonedDateTime = ZonedDateTime.now(),
    ) = TildeltVeilederMsg(
        aktorId = aktorId,
        veilederId = veilederId,
        tilordnet = tilordnet,
    )

    fun lagPersonhendelseAdressebeskyttelse(
        personidenter: List<String>,
        gradering: Gradering,
    ) = lagPersonhendelse(
        personidenter = personidenter,
        navn = null,
        adressebeskyttelse = Adressebeskyttelse(gradering),
        opplysningsType = OpplysningsType.ADRESSEBESKYTTELSE_V1,
    )

    fun lagPersonhendelseNavn(
        personidenter: List<String>,
        fornavn: String,
        mellomnavn: String?,
        etternavn: String,
    ) = lagPersonhendelse(
        personidenter = personidenter,
        navn =
            Navn(
                fornavn,
                mellomnavn,
                etternavn,
                "forkortetNavn",
                null,
                LocalDate.now(),
            ),
        adressebeskyttelse = null,
        opplysningsType = OpplysningsType.NAVN_V1,
    )

    fun lagPersonhendelseFalskIdentitet(personidenter: List<String>) = lagPersonhendelse(
        personidenter = personidenter,
        navn = null,
        adressebeskyttelse = null,
        opplysningsType = OpplysningsType.FALSK_ID_V1,
    )

    private fun lagPersonhendelse(
        personidenter: List<String>,
        navn: Navn?,
        adressebeskyttelse: Adressebeskyttelse?,
        opplysningsType: OpplysningsType,
    ) = Personhendelse(
        UUID.randomUUID().toString(),
        personidenter,
        "FREG",
        ZonedDateTime.now().toInstant(),
        opplysningsType.toString(),
        Endringstype.OPPRETTET,
        null,
        adressebeskyttelse,
        navn,
    )
}
