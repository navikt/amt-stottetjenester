package no.nav.arrangor.ansatt

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class AnsattRolleServiceTest(
    private val rolleService: AnsattRolleService,
    private val arrangorRepository: ArrangorRepository,
) : IntegrationTest() {
    private lateinit var ansatt: AnsattDbo

    private lateinit var arrangorOne: ArrangorRepository.ArrangorDbo
    private lateinit var arrangorTwo: ArrangorRepository.ArrangorDbo
    private lateinit var arrangorThree: ArrangorRepository.ArrangorDbo

    @BeforeEach
    fun setUp() {
        arrangorOne = testDatabase.insertArrangor()
        arrangorTwo = testDatabase.insertArrangor()
        arrangorThree = testDatabase.insertArrangor()
    }

    @Test
    fun `mapAltinnRollerTilArrangorListeForNyAnsatt - alle arrangorer finnes - returnerer liste`() {
        val altinnRoller =
            listOf(
                AltinnRolle(arrangorOne.organisasjonsnummer, listOf(AnsattRolle.KOORDINATOR)),
                AltinnRolle(arrangorTwo.organisasjonsnummer, listOf(AnsattRolle.VEILEDER)),
            )

        val arrangorliste = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

        arrangorliste.size shouldBe 2
        val arrangor = arrangorliste.find { it.arrangorId == arrangorOne.id } ?: throw RuntimeException("Mangler arrangør")

        assertSoftly(arrangor) {
            roller.size shouldBe 1
            roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
            roller[0].erGyldig() shouldBe true
            veileder.size shouldBe 0
            koordinator.size shouldBe 0
        }

        val arrangor2 = arrangorliste.find { it.arrangorId == arrangorTwo.id } ?: throw RuntimeException("Mangler arrangør")
        assertSoftly(arrangor2) {
            roller.size shouldBe 1
            roller[0].rolle shouldBe AnsattRolle.VEILEDER
            roller[0].erGyldig() shouldBe true
            veileder.size shouldBe 0
            koordinator.size shouldBe 0
        }
    }

    @Test
    fun `mapAltinnRollerTilArrangorListeForNyAnsatt - en arrangor mangler - lagrer arrangor som mangler`() {
        val nyttOrgnummer = "812233445"
        mockVirksomhet(
            Virksomhet(
                organisasjonsnummer = nyttOrgnummer,
                navn = "Ny Arrangør AS",
                overordnetEnhetOrganisasjonsnummer = "956789123",
                overordnetEnhetNavn = "overordnetArrangor",
            ),
        )
        val altinnRoller =
            listOf(
                AltinnRolle(arrangorOne.organisasjonsnummer, listOf(AnsattRolle.KOORDINATOR)),
                AltinnRolle(nyttOrgnummer, listOf(AnsattRolle.VEILEDER)),
            )

        val arrangorliste = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

        arrangorliste.size shouldBe 2
        arrangorliste.find { it.arrangorId == arrangorOne.id } shouldNotBe null
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - to nye roller, en eksisterende rolle - returnerer objekt med korrekte roller`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = emptyList(),
                        ),
                    ),
                lastSynchronized = LocalDateTime.now().minusMonths(1),
            )

        mockAltinnRoller(
            ansatt.personident,
            mapOf(
                arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR),
                arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
            ),
        )

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 2

        val arrangorOne = ansattDbo.arrangorer.find { it.arrangorId == arrangorOne.id }
        assertSoftly(arrangorOne.shouldNotBeNull()) {
            roller.size shouldBe 1
            roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
            roller[0].gyldigTil shouldBe null
        }

        val arrangorTwo = ansattDbo.arrangorer.find { it.arrangorId == arrangorTwo.id }
        assertSoftly(arrangorTwo.shouldNotBeNull()) {
            roller.size shouldBe 2
            roller.map { it.rolle } shouldContainAll listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)
        }

        ansattDbo.lastSynchronized.shouldBeWithin(Duration.ofSeconds(10), LocalDateTime.now())
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - eksisterende rolle er utlopt men aktivert igjen fra altinn - ny rolle opprettes`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(5))),
                            veileder = emptyList(),
                            koordinator = emptyList(),
                        ),
                    ),
            )

        mockAltinnRoller(
            ansatt.personident,
            mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR)),
        )

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 1

        val arrangorOne = ansattDbo.arrangorer[0]
        arrangorOne.roller.size shouldBe 2

        val gyldigRolle = arrangorOne.roller.first { it.erGyldig() }
        assertSoftly(gyldigRolle) {
            rolle shouldBe AnsattRolle.KOORDINATOR
            gyldigTil shouldBe null
            erGyldig() shouldBe true
        }

        val deaktivertRolle = arrangorOne.roller.first { !it.erGyldig() }
        assertSoftly(deaktivertRolle) {
            rolle shouldBe AnsattRolle.KOORDINATOR
            gyldigTil shouldNotBe null
            erGyldig() shouldBe false
        }
    }

    @Test
    fun `mapAltinnRollerTilArrangorListeForNyAnsatt - nye roller, ny arrangor - returnerer korrekte roller og lagrer ny arrangor`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorOne.id,
                            listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(5))),
                            emptyList(),
                            emptyList(),
                        ),
                    ),
            )

        val nyArrangorOrgnummer = "923456789"

        mockAltinnRoller(
            ansatt.personident,
            mapOf(nyArrangorOrgnummer to listOf(AnsattRolle.KOORDINATOR)),
        )

        mockVirksomhet(
            Virksomhet(
                organisasjonsnummer = nyArrangorOrgnummer,
                navn = "Ny Arrangør AS",
                overordnetEnhetOrganisasjonsnummer = "456",
                overordnetEnhetNavn = "overordnetArrangor",
            ),
        )

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 2

        val nyArrangor = ansattDbo.arrangorer.find { it.arrangorId != arrangorOne.id }
        assertSoftly(nyArrangor.shouldNotBeNull()) {
            roller.size shouldBe 1
            roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
            roller[0].gyldigTil shouldBe null
        }

        arrangorRepository.get(nyArrangorOrgnummer) shouldNotBe null
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - likt i altinn og database - ingen endringer`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
                        ),
                    ),
            )

        mockAltinnRoller(
            ansatt.personident,
            mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR)),
        )

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe false }
                .data

        ansattDbo.arrangorer.size shouldBe 1
        ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
        ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
        ansattDbo.arrangorer[0].roller[0].gyldigTil shouldBe null
        ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldBe null
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - har roller i database, men ingen i altinn - setter roller til ugyldig`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = emptyList(),
                        ),
                    ),
            )

        mockAltinnRoller(ansatt.personident, emptyList())

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 1
        ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
        ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
        ansattDbo.arrangorer[0].roller[0].gyldigTil shouldNotBe null
        ansattDbo.arrangorer[0].roller[0].erGyldig() shouldBe false
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - mister tilgang, er veileder, har lagt til deltakerlister - fjerner roller og tilganger`() {
        val deltakerId = UUID.randomUUID()
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR), RolleDbo(AnsattRolle.VEILEDER)),
                            veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.VEILEDER)),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
                        ),
                    ),
            )

        mockAltinnRoller(ansatt.personident, emptyList())

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 1
        ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
        ansattDbo.arrangorer[0].roller.size shouldBe 2
        ansattDbo.arrangorer[0]
            .roller
            .filter { it.erGyldig() }
            .size shouldBe 0
        ansattDbo.arrangorer[0].veileder[0].gyldigTil shouldNotBe null
        ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldNotBe null
        ansattDbo.arrangorer[0].koordinator[0].erGyldig() shouldBe false
    }

    // getAnsattDboMedOppdaterteRoller -
    @Test
    fun `har deaktivert rolle blir tildelt ny rolle - legger til ny rolle fjerner ikke deaktiver rolle`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now())),
                            veileder = emptyList(),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
                        ),
                    ),
            )

        mockAltinnRoller(ansatt.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR)))

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe true }
                .data

        ansattDbo.arrangorer.size shouldBe 1
        ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
        ansattDbo.arrangorer[0].roller.size shouldBe 2
        ansattDbo.arrangorer[0].roller.any { it.rolle == AnsattRolle.KOORDINATOR && it.erGyldig() } shouldBe true
        ansattDbo.arrangorer[0].roller.any { it.rolle == AnsattRolle.KOORDINATOR && !it.erGyldig() } shouldBe true
    }

    @Test
    fun `getAnsattDboMedOppdaterteRoller - har samme rolle flere ganger og rollen er aktiv i altinn - ingen endringer`() {
        ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller =
                                listOf(
                                    RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(7)),
                                    RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = null),
                                ),
                            veileder = emptyList(),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
                        ),
                    ),
            )

        mockAltinnRoller(
            ansatt.personident,
            mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR)),
        )

        val ansattDbo =
            rolleService
                .getAnsattDboMedOppdaterteRoller(ansatt)
                .also { it.isUpdated shouldBe false }
                .data

        ansattDbo.arrangorer.size shouldBe 1
        ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
        ansattDbo.arrangorer[0].roller.size shouldBe 2

        ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
        ansattDbo.arrangorer[0].roller[0].gyldigTil shouldNotBe null

        ansattDbo.arrangorer[0].roller[1].rolle shouldBe AnsattRolle.KOORDINATOR
        ansattDbo.arrangorer[0].roller[1].gyldigTil shouldBe null

        ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldBe null
    }
}
