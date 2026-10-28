package no.nav.arrangor.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.deltaker.DeltakerRepository
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.kafka.model.AnsattPersonaliaDto
import no.nav.arrangor.kafka.model.Deltaker
import no.nav.arrangor.kafka.model.DeltakerStatus
import no.nav.arrangor.kafka.model.DeltakerStatusType
import no.nav.arrangor.kafka.model.VirksomhetDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class ConsumerServiceTest(
    private val consumerService: ConsumerService,
    private val arrangorRepository: ArrangorRepository,
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
    private val deltakerRepository: DeltakerRepository,
    private val meterRegistry: MeterRegistry,
) : IntegrationTest() {
    val personIdent = "12345678910"
    val personId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() = mockPerson(
        personident = personIdent,
        id = personId,
        fornavn = "Test",
        mellomnavn = null,
        etternavn = "Testersen",
    )

    @Test
    fun `handleVirksomhetEndring - finnes i db med annet navn - arrangornavn oppdateres i db`() {
        val overordnetArrangorId = UUID.randomUUID()
        val overordnetOrgnummer = "888887776"
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = overordnetArrangorId,
                navn = "Overordnet arrangør",
                organisasjonsnummer = overordnetOrgnummer,
                overordnetArrangorId = null,
            ),
        )

        val arrangorId = UUID.randomUUID()
        val orgnummer = "999988888"
        mockVirksomhet(
            Virksomhet(
                organisasjonsnummer = orgnummer,
                navn = "Arrangør",
                overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer,
                overordnetEnhetNavn = "Overordnet arrangør",
            ),
        )
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = arrangorId,
                navn = UUID.randomUUID().toString(),
                organisasjonsnummer = orgnummer,
                overordnetArrangorId = overordnetArrangorId,
            ),
        )

        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = orgnummer,
                navn = "Nytt navn",
                overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer,
            ),
        )

        val oppdatertArrangor = arrangorRepository.get(arrangorId)
        oppdatertArrangor?.navn shouldBe "Nytt navn"
        oppdatertArrangor?.overordnetArrangorId shouldBe overordnetArrangorId
    }

    @Test
    fun `handleVirksomhetEndring - samme arrangørdata - teller hendelse men ikke arrangørendring`() {
        val arrangorId = UUID.randomUUID()
        val orgnummer = "999988888"
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = arrangorId,
                navn = "Arrangør",
                organisasjonsnummer = orgnummer,
                overordnetArrangorId = null,
            ),
        )
        val endredeArrangorerBefore = metricCount("amt_arrangor_endrede_arrangorer")
        val mottatteHendelserBefore = metricCount("amt_arrangor_consumed_virksomhet")

        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = orgnummer,
                navn = "Arrangør",
                overordnetEnhetOrganisasjonsnummer = null,
            ),
        )

        metricCount("amt_arrangor_endrede_arrangorer") shouldBe endredeArrangorerBefore
        metricCount("amt_arrangor_consumed_virksomhet") shouldBe mottatteHendelserBefore + 1
    }

    @Test
    fun `handleVirksomhetEndring - finnes med annen overordnet arrangor, ny overordnet arrangor finnes - overordnet arrangor oppdateres`() {
        val overordnetArrangorId = UUID.randomUUID()
        val overordnetOrgnummer = "888887776"
        val nyOverordnetArrangorId = UUID.randomUUID()
        val nyOverordnetOrgnummer = "811122222"
        val arrangorId = UUID.randomUUID()
        val orgnummer = "999988888"
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = overordnetArrangorId,
                navn = "Overordnet navn",
                organisasjonsnummer = overordnetOrgnummer,
                overordnetArrangorId = null,
            ),
        )
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = nyOverordnetArrangorId,
                navn = "Ny overordnet navn",
                organisasjonsnummer = nyOverordnetOrgnummer,
                overordnetArrangorId = null,
            ),
        )
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = arrangorId,
                navn = "Navn",
                organisasjonsnummer = orgnummer,
                overordnetArrangorId = overordnetArrangorId,
            ),
        )

        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = orgnummer,
                navn = "Nytt navn",
                overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer,
            ),
        )

        val oppdatertArrangor = arrangorRepository.get(arrangorId)
        oppdatertArrangor?.navn shouldBe "Nytt navn"
        oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangorId
    }

    // handleVirksomhetEndring
    @Test
    fun `finnes med annen overordnet arrangor,ny overordnet arrangor finnes ikke - ny overordnet arrangor lagres`() {
        val overordnetArrangorId = UUID.randomUUID()
        val overordnetOrgnummer = "888887776"
        val nyOverordnetOrgnummer = "811122222"
        val arrangorId = UUID.randomUUID()
        val orgnummer = "999988888"
        mockVirksomhet(
            Virksomhet(
                organisasjonsnummer = nyOverordnetOrgnummer,
                navn = "Ny Overordnet arrangør",
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null,
            ),
        )
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = overordnetArrangorId,
                navn = "Overordnet navn",
                organisasjonsnummer = overordnetOrgnummer,
                overordnetArrangorId = null,
            ),
        )
        arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = arrangorId,
                navn = "Navn",
                organisasjonsnummer = orgnummer,
                overordnetArrangorId = overordnetArrangorId,
            ),
        )

        consumerService.handleVirksomhetEndring(
            VirksomhetDto(
                organisasjonsnummer = orgnummer,
                navn = "Nytt navn",
                overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer,
            ),
        )

        val oppdatertArrangor = arrangorRepository.get(arrangorId)
        oppdatertArrangor?.navn shouldBe "Nytt navn"
        oppdatertArrangor?.overordnetArrangorId shouldNotBe overordnetArrangorId
        val nyOverordnetArrangor = arrangorRepository.get(nyOverordnetOrgnummer)
        nyOverordnetArrangor shouldNotBe null
        nyOverordnetArrangor?.navn shouldBe "Ny Overordnet arrangør"
        oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangor?.id
    }

    @Test
    fun `handleAnsattPersonalia - ansatt har endringer - oppdaterer ansatt`() {
        val ansatt =
            AnsattDbo(
                id = UUID.randomUUID(),
                personident = "123456",
                personId = UUID.randomUUID(),
                fornavn = "Test",
                mellomnavn = "Mellom",
                etternavn = "Testersen",
                arrangorer = emptyList(),
            )
        ansattRepository.insertOrUpdate(ansatt)

        val nyPersonalia =
            AnsattPersonaliaDto(
                ansatt.personId,
                "ny ident",
                "nytt",
                null,
                "navn",
            )

        consumerService.handleAnsattPersonalia(nyPersonalia)

        val oppdatertAnsatt = ansattRepository.get(ansatt.id)
        assertSoftly(oppdatertAnsatt.shouldNotBeNull()) {
            personident shouldBe nyPersonalia.personident
            fornavn shouldBe nyPersonalia.fornavn
            mellomnavn shouldBe nyPersonalia.mellomnavn
            etternavn shouldBe nyPersonalia.etternavn
        }
    }

    @Test
    fun `handleAnsattPersonalia - ansatt har ikke endringer - oppdaterer ikke ansatt`() {
        val ansatt =
            AnsattDbo(
                id = UUID.randomUUID(),
                personident = "123456",
                personId = UUID.randomUUID(),
                fornavn = "Test",
                mellomnavn = "Mellom",
                etternavn = "Testersen",
                arrangorer = emptyList(),
                modifiedAt = LocalDateTime.now().minusMonths(1),
            )
        ansattRepository.insertOrUpdate(ansatt)

        val personalia =
            AnsattPersonaliaDto(
                ansatt.personId,
                ansatt.personident,
                ansatt.fornavn,
                ansatt.mellomnavn,
                ansatt.etternavn,
            )

        consumerService.handleAnsattPersonalia(personalia)

        val faktiskAnsatt = ansattRepository.get(ansatt.id)
        assertSoftly(faktiskAnsatt.shouldNotBeNull()) {
            personident shouldBe ansatt.personident
            fornavn shouldBe ansatt.fornavn
            mellomnavn shouldBe ansatt.mellomnavn
            etternavn shouldBe ansatt.etternavn
            modifiedAt.shouldBeWithin(Duration.ofSeconds(1), ansatt.modifiedAt)
        }
    }

    @Test
    fun `handleDeltakerEndret - avsluttende status, aktive veiledere - setter gyldigTil på aktive veiledere 50 dager frem i tid`() {
        val deltakerId1 = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
                ),
            )
        val deltaker =
            Deltaker(
                id = deltakerId1,
                status = DeltakerStatus(DeltakerStatusType.HAR_SLUTTET, LocalDateTime.now(), LocalDateTime.now()),
            )

        consumerService.handleDeltakerEndring(deltakerId1, deltaker)

        val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(50)

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        deltakerRepository.get(deltakerId1)?.status?.type shouldBe DeltakerStatusType.HAR_SLUTTET
    }

    @Test
    fun `handleDeltakerEndret - avsluttende status, aktive veiledere, har lest deltaker tidligere - oppdaterer ikke`() {
        val deltakerId1 = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
                ),
            )
        val deltaker =
            Deltaker(
                id = deltakerId1,
                status = DeltakerStatus(DeltakerStatusType.HAR_SLUTTET, LocalDateTime.now(), LocalDateTime.now()),
            )
        deltakerRepository.insertOrUpdate(deltaker)

        consumerService.handleDeltakerEndring(deltakerId1, deltaker)

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil shouldBe null
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil shouldBe null
        }
    }

    @Test
    fun `handleDeltakerEndret - skjult status, aktive veiledere - setter gyldigTil på aktive veiledere til 50 dager frem i tid`() {
        val deltakerId1 = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
                ),
            )
        val deltaker =
            Deltaker(
                id = deltakerId1,
                status = DeltakerStatus(DeltakerStatusType.PABEGYNT_REGISTRERING, LocalDateTime.now(), LocalDateTime.now()),
            )

        consumerService.handleDeltakerEndring(deltakerId1, deltaker)

        val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(50)

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }
    }

    @Test
    fun `handleDeltakerEndret - fremtidige deaktiverte veiledere, ny status - reaktiverer veiledere`() {
        val deltakerId1 = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER, gyldigTil = ZonedDateTime.now().plusDays(10)),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER, gyldigTil = ZonedDateTime.now().plusDays(10)),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
                ),
            )
        val deltaker =
            Deltaker(
                id = deltakerId1,
                status = DeltakerStatus(
                    DeltakerStatusType.IKKE_AKTUELL,
                    LocalDateTime.now().minusDays(8),
                    LocalDateTime.now().minusDays(8),
                ),
            )
        deltakerRepository.insertOrUpdate(deltaker)

        consumerService.handleDeltakerEndring(
            deltakerId1,
            deltaker.copy(
                status = DeltakerStatus(
                    DeltakerStatusType.DELTAR,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                ),
            ),
        )

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil shouldBe null

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil shouldBe null

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        deltakerRepository.get(deltakerId1)?.status?.type shouldBe DeltakerStatusType.DELTAR
    }

    @Test
    fun `handleDeltakerEndret - deltaker slettet, aktive veiledere - setter gyldigTil på aktive veiledere til 50 dager frem i tid`() {
        val deltakerId1 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
                ),
            )
        consumerService.handleDeltakerEndring(deltakerId1, null)

        val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(50)

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)
        }
    }

    // handleDeltakerEndret
    @Test
    fun `avsluttet status, inaktive veiledere frem i tid, til DELTAR - fjerner ikke passert gyldigTil for veiledere`() {
        val deltakerId1 = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        val arrangor = UUID.randomUUID()

        val ansatt1 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(
                        deltakerId1,
                        VeilederType.MEDVEILEDER,
                        gyldigTil = ZonedDateTime.now().plusDays(8),
                    ),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
                ),
            )
        val ansatt2 =
            veileder(
                arrangor,
                listOf(
                    VeilederDeltakerDbo(
                        deltakerId1,
                        VeilederType.VEILEDER,
                        gyldigTil = ZonedDateTime.now().minusDays(2),
                    ),
                    VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
                ),
            )
        val deltaker =
            Deltaker(
                id = deltakerId1,
                status = DeltakerStatus(DeltakerStatusType.DELTAR, LocalDateTime.now(), LocalDateTime.now()),
            )

        consumerService.handleDeltakerEndring(deltakerId1, deltaker)

        val oppdatertAnsatt1 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id)
        oppdatertAnsatt1.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil
                .shouldBe(null)

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }

        val oppdatertAnsatt2 = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id)
        oppdatertAnsatt2.forEach { arr ->
            arr.veileder
                .first { it.deltakerId == deltakerId1 }
                .gyldigTil!!
                .shouldBeWithin(Duration.ofSeconds(10), ZonedDateTime.now().minusDays(2))

            arr.veileder.first { it.deltakerId == deltakerId2 }.gyldigTil shouldBe null
        }
    }

    private fun veileder(
        arrangor: UUID,
        veilderDeltakere: List<VeilederDeltakerDbo>,
    ): AnsattDbo = testDatabase.insertAnsatt(
        arrangorer =
            listOf(
                ArrangorDbo(
                    arrangor,
                    listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
                    veilderDeltakere,
                    emptyList(),
                ),
            ),
    )

    private fun metricCount(name: String): Double = meterRegistry.counter(name).count()
}
