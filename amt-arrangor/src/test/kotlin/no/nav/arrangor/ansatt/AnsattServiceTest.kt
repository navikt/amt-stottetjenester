package no.nav.arrangor.ansatt

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.verify
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.kafka.ProducerService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class AnsattServiceTest(
    private val ansattService: AnsattService,
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
    @MockkBean(relaxed = true) private val producerService: ProducerService,
    @MockkBean(relaxed = true) @Suppress("unused") private val metricsService: MetricsService,
) : IntegrationTest() {
    private lateinit var arrangorOne: ArrangorRepository.ArrangorDbo
    private lateinit var arrangorTwo: ArrangorRepository.ArrangorDbo

    @BeforeEach
    fun setUp() {
        resetClientMocks()
        clearMocks(producerService)
        arrangorOne = testDatabase.insertArrangor()
        arrangorTwo = testDatabase.insertArrangor()
    }

    @Test
    fun `get - returnerer arrangorer fra normaliserte tabeller`() {
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorOne.id,
                    listOf(RolleDbo(AnsattRolle.VEILEDER)),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        ansattArrangorRepository.replaceForAnsatt(
            ansatt.id,
            listOf(
                ArrangorDbo(
                    arrangorTwo.id,
                    listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        val resultat = ansattService.get(ansatt.id).shouldNotBeNull()

        resultat.arrangorer shouldHaveSize 1
        resultat.arrangorer.single().arrangorId shouldBe arrangorTwo.id
        resultat.arrangorer.single().roller shouldBe listOf(AnsattRolle.KOORDINATOR)
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - bruker normalisert rolle for tilgangskontroll`() {
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorOne.id,
                    listOf(RolleDbo(AnsattRolle.VEILEDER)),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        ansattArrangorRepository.replaceForAnsatt(
            ansatt.id,
            listOf(
                ArrangorDbo(
                    arrangorOne.id,
                    listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        ansattService.oppdaterVeiledereForDeltaker(
            ansatt.personident,
            UUID.randomUUID(),
            AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorOne.id,
                veilederSomLeggesTil = emptyList(),
                veilederSomFjernes = emptyList(),
            ),
        )
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - ansatt uten koordinatorrolle gir ikke tilgang`() {
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorOne.id,
                    listOf(RolleDbo(AnsattRolle.VEILEDER)),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        assertThrows<IllegalArgumentException> {
            ansattService.oppdaterVeiledereForDeltaker(
                ansatt.personident,
                UUID.randomUUID(),
                AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                    arrangorId = arrangorOne.id,
                    veilederSomLeggesTil = emptyList(),
                    veilederSomFjernes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - deaktivert koordinatorrolle gir ikke tilgang`() {
        val ansatt = testDatabase.insertAnsatt(
            arrangorer = listOf(
                ArrangorDbo(
                    arrangorOne.id,
                    listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(1))),
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        assertThrows<IllegalArgumentException> {
            ansattService.oppdaterVeiledereForDeltaker(
                ansatt.personident,
                UUID.randomUUID(),
                AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                    arrangorId = arrangorOne.id,
                    veilederSomLeggesTil = emptyList(),
                    veilederSomFjernes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `oppdaterRoller - ansatt mister eneste rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten arrangoren`() {
        val ansattDbo =
            testDatabase.insertAnsatt(
                lastSynchronized = LocalDateTime.now().minusHours(2),
                arrangorer =
                    listOf(
                        ArrangorDbo(arrangorOne.id, listOf(RolleDbo(AnsattRolle.VEILEDER)), emptyList(), emptyList()),
                        ArrangorDbo(arrangorTwo.id, listOf(RolleDbo(AnsattRolle.VEILEDER)), emptyList(), emptyList()),
                    ),
            )
        mockAltinnRoller(
            ansattDbo.personident,
            mapOf(arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)),
        )

        ansattService.get(ansattDbo.id)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansattDbo.id)
        oppdatertAnsatt?.arrangorer?.size shouldBe 2
        oppdatertAnsatt
            ?.arrangorer
            ?.find { it.arrangorId == arrangorOne.id }
            ?.roller
            ?.first()
            ?.erGyldig() shouldBe false
        oppdatertAnsatt
            ?.arrangorer
            ?.find { it.arrangorId == arrangorTwo.id }
            ?.roller
            ?.first()
            ?.erGyldig() shouldBe true

        verify(exactly = 1) {
            producerService.publishAnsatt(match { it.arrangorer.size == 1 && it.arrangorer.first().arrangorId == arrangorTwo.id })
        }
    }

    @Test
    fun `oppdaterRoller - ansatt mister en rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten rollen`() {
        val ansattDbo =
            testDatabase.insertAnsatt(
                lastSynchronized = LocalDateTime.now().minusHours(2),
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorOne.id,
                            listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
                            emptyList(),
                            emptyList(),
                        ),
                    ),
            )
        mockAltinnRoller(
            ansattDbo.personident,
            mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)),
        )

        ansattService.get(ansattDbo.id)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansattDbo.id)
        oppdatertAnsatt?.arrangorer?.size shouldBe 1
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.size shouldBe 2
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.find { it.rolle == AnsattRolle.KOORDINATOR }
            ?.erGyldig() shouldBe false
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.find { it.rolle == AnsattRolle.VEILEDER }
            ?.erGyldig() shouldBe true

        verify(exactly = 1) {
            producerService.publishAnsatt(
                match {
                    it.arrangorer
                        .first()
                        .roller.size == 1 &&
                        it.arrangorer
                            .first()
                            .roller
                            .first() == AnsattRolle.VEILEDER
                },
            )
        }
    }

    @Test
    fun `oppdaterRoller - ansatt mister en rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten koordinator for`() {
        val koordinatorFor = UUID.randomUUID()
        val ansattDbo =
            testDatabase.insertAnsatt(
                lastSynchronized = LocalDateTime.now().minusHours(2),
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorOne.id,
                            listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
                            emptyList(),
                            listOf(
                                KoordinatorsDeltakerlisteDbo(koordinatorFor),
                            ),
                        ),
                    ),
            )
        ansattArrangorRepository.replaceForAnsatt(ansattDbo.id, ansattDbo.arrangorer)
        mockAltinnRoller(ansattDbo.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)))

        ansattService.get(ansattDbo.id)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansattDbo.id)
        oppdatertAnsatt?.arrangorer?.size shouldBe 1
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.size shouldBe 2
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.find { it.rolle == AnsattRolle.KOORDINATOR }
            ?.erGyldig() shouldBe false
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.find { it.rolle == AnsattRolle.VEILEDER }
            ?.erGyldig() shouldBe true
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.koordinator
            ?.first()
            ?.erGyldig() shouldBe false
        val normalisert = ansattArrangorRepository.getArrangorerForAnsatt(ansattDbo.id).single()
        normalisert.roller.first { it.rolle == AnsattRolle.KOORDINATOR }.erGyldig() shouldBe false
        normalisert.koordinator.single().erGyldig() shouldBe false

        verify(exactly = 1) {
            producerService.publishAnsatt(
                match {
                    it.arrangorer
                        .first()
                        .roller.size == 1 &&
                        it.arrangorer
                            .first()
                            .roller
                            .first() == AnsattRolle.VEILEDER &&
                        it.arrangorer
                            .first()
                            .koordinator
                            .isEmpty()
                },
            )
        }
    }

    @Test
    fun `oppdaterRoller - ansatt får tilbake deaktivert rolle - oppretter ny rolle og publiserer ansatt`() {
        val ansattDbo =
            testDatabase.insertAnsatt(
                lastSynchronized = LocalDateTime.now().minusHours(2),
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorOne.id,
                            listOf(RolleDbo(AnsattRolle.VEILEDER, gyldigTil = ZonedDateTime.now().minusDays(7))),
                            emptyList(),
                            emptyList(),
                        ),
                    ),
            )
        ansattArrangorRepository.replaceForAnsatt(ansattDbo.id, ansattDbo.arrangorer)
        mockAltinnRoller(ansattDbo.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)))

        ansattService.get(ansattDbo.id)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansattDbo.id)
        oppdatertAnsatt?.arrangorer?.size shouldBe 1
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.size shouldBe 2
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.any { it.rolle == AnsattRolle.VEILEDER && it.erGyldig() } shouldBe true
        oppdatertAnsatt
            ?.arrangorer
            ?.first()
            ?.roller
            ?.any { it.rolle == AnsattRolle.VEILEDER && !it.erGyldig() } shouldBe true
        val normaliserteRoller = ansattArrangorRepository.getArrangorerForAnsatt(ansattDbo.id).single().roller
        normaliserteRoller shouldHaveSize 2
        normaliserteRoller.any { it.rolle == AnsattRolle.VEILEDER && it.erGyldig() } shouldBe true
        normaliserteRoller.any { it.rolle == AnsattRolle.VEILEDER && !it.erGyldig() } shouldBe true

        verify(exactly = 1) {
            producerService.publishAnsatt(
                match {
                    it.arrangorer
                        .first()
                        .roller.size == 1 &&
                        it.arrangorer
                            .first()
                            .roller
                            .first() == AnsattRolle.VEILEDER &&
                        it.arrangorer
                            .first()
                            .koordinator
                            .isEmpty()
                },
            )
        }
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - ansatt har ikke koordinatortilgang - kaster feil`() {
        val ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorOne.id,
                            listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            emptyList(),
                            emptyList(),
                        ),
                    ),
            )

        assertThrows<IllegalArgumentException> {
            ansattService.oppdaterVeiledereForDeltaker(
                ansatt.personident,
                UUID.randomUUID(),
                AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                    arrangorId = arrangorOne.id,
                    veilederSomLeggesTil =
                        listOf(
                            AnsattAPI.VeilederAnsatt(
                                UUID.randomUUID(),
                                VeilederType.VEILEDER,
                            ),
                        ),
                    veilederSomFjernes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - skal legge til ansatt som veileder - ansatt blir oppdatert`() {
        val deltakerId = testDatabase.insertDeltaker()
        val koordinator =
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
        val veileder1 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder =
                                listOf(
                                    VeilederDeltakerDbo(
                                        deltakerId = deltakerId,
                                        veilederType = VeilederType.MEDVEILEDER,
                                    ),
                                ),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val veileder2 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder = emptyList(),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val request =
            AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorOne.id,
                veilederSomLeggesTil =
                    listOf(
                        AnsattAPI.VeilederAnsatt(
                            veileder2.id,
                            VeilederType.VEILEDER,
                        ),
                    ),
                veilederSomFjernes = emptyList(),
            )

        ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

        val veileder1Db = hentAnsattMedArrangorer(veileder1.id)
        val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
        veileder1Arrangor?.veileder?.size shouldBe 1
        veileder1Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig()
        } shouldNotBe null

        val veileder2Db = hentAnsattMedArrangorer(veileder2.id)
        val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
        veileder2Arrangor?.veileder?.size shouldBe 1
        veileder2Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig()
        } shouldNotBe null
        ansattArrangorRepository
            .getArrangorerForAnsatt(veileder2.id)
            .single()
            .veileder
            .single()
            .let {
                it.deltakerId shouldBe deltakerId
                it.veilederType shouldBe VeilederType.VEILEDER
                it.erGyldig() shouldBe true
            }
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - skal fjerne ansatt som veileder - ansatt blir oppdatert`() {
        val deltakerId = testDatabase.insertDeltaker()
        val koordinator =
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
        val veileder1 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder =
                                listOf(
                                    VeilederDeltakerDbo(
                                        deltakerId = deltakerId,
                                        veilederType = VeilederType.MEDVEILEDER,
                                    ),
                                ),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val veileder2 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder =
                                listOf(
                                    VeilederDeltakerDbo(
                                        deltakerId = deltakerId,
                                        veilederType = VeilederType.VEILEDER,
                                    ),
                                ),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        ansattArrangorRepository.replaceForAnsatt(veileder1.id, veileder1.arrangorer)
        ansattArrangorRepository.replaceForAnsatt(veileder2.id, veileder2.arrangorer)
        val request =
            AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorOne.id,
                veilederSomLeggesTil = emptyList(),
                veilederSomFjernes =
                    listOf(
                        AnsattAPI.VeilederAnsatt(
                            veileder1.id,
                            VeilederType.MEDVEILEDER,
                        ),
                    ),
            )

        ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

        val veileder1Db = hentAnsattMedArrangorer(veileder1.id)
        val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
        veileder1Arrangor?.veileder?.size shouldBe 1
        veileder1Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig()
        } shouldBe null
        veileder1Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && !it.erGyldig()
        } shouldNotBe null

        val veileder2Db = hentAnsattMedArrangorer(veileder2.id)
        val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
        veileder2Arrangor?.veileder?.size shouldBe 1
        veileder2Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig()
        } shouldNotBe null
        ansattArrangorRepository
            .getArrangorerForAnsatt(veileder1.id)
            .single()
            .veileder
            .single()
            .erGyldig() shouldBe false
        ansattArrangorRepository
            .getArrangorerForAnsatt(veileder2.id)
            .single()
            .veileder
            .single()
            .erGyldig() shouldBe true
    }

    @Test
    fun `oppdaterVeiledereForDeltaker - skal legge til og fjerne ansatt som veileder - ansatte blir oppdatert`() {
        // Arrange
        val deltakerId = testDatabase.insertDeltaker()
        val koordinator =
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
        val veileder1 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder =
                                listOf(
                                    VeilederDeltakerDbo(
                                        deltakerId = deltakerId,
                                        veilederType = VeilederType.MEDVEILEDER,
                                    ),
                                ),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val veileder2 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder =
                                listOf(
                                    VeilederDeltakerDbo(
                                        deltakerId = deltakerId,
                                        veilederType = VeilederType.VEILEDER,
                                    ),
                                ),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val veileder3 =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder = emptyList(),
                            koordinator = emptyList(),
                        ),
                    ),
            )
        val request =
            AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorOne.id,
                veilederSomLeggesTil =
                    listOf(
                        AnsattAPI.VeilederAnsatt(
                            veileder1.id,
                            VeilederType.VEILEDER,
                        ),
                        AnsattAPI.VeilederAnsatt(
                            veileder2.id,
                            VeilederType.MEDVEILEDER,
                        ),
                        AnsattAPI.VeilederAnsatt(
                            veileder3.id,
                            VeilederType.MEDVEILEDER,
                        ),
                    ),
                veilederSomFjernes =
                    listOf(
                        AnsattAPI.VeilederAnsatt(
                            veileder1.id,
                            VeilederType.MEDVEILEDER,
                        ),
                        AnsattAPI.VeilederAnsatt(
                            veileder2.id,
                            VeilederType.VEILEDER,
                        ),
                    ),
            )

        // Act
        ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

        // Assert
        val veileder1Db = hentAnsattMedArrangorer(veileder1.id)
        val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }.shouldNotBeNull()
        veileder1Arrangor.veileder.size shouldBe 2
        veileder1Arrangor.veileder.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig()
        } shouldNotBe null
        veileder1Arrangor.veileder.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && !it.erGyldig()
        } shouldNotBe null

        val veileder2Db = hentAnsattMedArrangorer(veileder2.id)
        val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }.shouldNotBeNull()
        veileder2Arrangor.veileder.size shouldBe 2
        veileder2Arrangor.veileder.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && !it.erGyldig()
        } shouldNotBe null
        veileder2Arrangor.veileder.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig()
        } shouldNotBe null

        val veileder1Fjerningstidspunkt = veileder1Arrangor.veileder
            .find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER }
            ?.gyldigTil
            .shouldNotBeNull()
            .toInstant()
        val veileder2Fjerningstidspunkt = veileder2Arrangor.veileder
            .find { it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER }
            ?.gyldigTil
            .shouldNotBeNull()
            .toInstant()
        veileder1Fjerningstidspunkt shouldBe veileder2Fjerningstidspunkt

        val veileder3Db = hentAnsattMedArrangorer(veileder3.id)
        val veileder3Arrangor = veileder3Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
        veileder3Arrangor?.veileder?.size shouldBe 1
        veileder3Arrangor?.veileder?.find {
            it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig()
        } shouldNotBe null
    }

    @Test
    fun `oppdaterVeileder - deaktivert tilgang for deltaker finnes - ny tilgang opprettes`() {
        val deltakerId = testDatabase.insertDeltaker()
        val koordinator =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = listOf(),
                            koordinator = listOf(),
                        ),
                    ),
            )
        val ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                            veileder = listOf(
                                VeilederDeltakerDbo(
                                    deltakerId,
                                    VeilederType.VEILEDER,
                                    gyldigTil = ZonedDateTime.now().minusDays(7),
                                ),
                            ),
                            koordinator = listOf(),
                        ),
                    ),
            )

        val request =
            AnsattAPI.OppdaterVeiledereForDeltakerRequest(
                arrangorId = arrangorOne.id,
                veilederSomLeggesTil =
                    listOf(
                        AnsattAPI.VeilederAnsatt(
                            ansatt.id,
                            VeilederType.VEILEDER,
                        ),
                    ),
                veilederSomFjernes = emptyList(),
            )

        ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansatt.id)
        val veileder = oppdatertAnsatt?.arrangorer?.first()?.veileder

        assertSoftly(veileder.shouldNotBeNull()) { veilederDeltakerDbos ->
            size shouldBe 2
            veilederDeltakerDbos[0].deltakerId shouldBe deltakerId
            veilederDeltakerDbos[1].deltakerId shouldBe deltakerId
            veilederDeltakerDbos.any { it.erGyldig() } shouldBe true
            veilederDeltakerDbos.any { !it.erGyldig() } shouldBe true
        }
    }

    @Test
    fun `fjernKoordinatorForDeltakerliste - skal fjerne sette gyldigTil til nå`() {
        val deltakerliste = KoordinatorsDeltakerlisteDbo(UUID.randomUUID())
        val koordinator =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = listOf(deltakerliste),
                        ),
                    ),
            )
        ansattArrangorRepository.replaceForAnsatt(koordinator.id, koordinator.arrangorer)

        ansattService.fjernKoordinatorForDeltakerliste(
            koordinator.personident,
            koordinator.arrangorer[0].arrangorId,
            deltakerliste.deltakerlisteId,
        )

        ansattRepository
            .get(koordinator.id)!!
            .arrangorer[0]
            .koordinator[0]
            .gyldigTil shouldNotBe null
        ansattArrangorRepository
            .getArrangorerForAnsatt(koordinator.id)
            .single()
            .koordinator
            .single()
            .erGyldig() shouldBe false
    }

    @Test
    fun `fjernTilgangerHosArrangor - koordinator skal fjernes`() {
        val deltakerliste = UUID.randomUUID()
        val arrangor = testDatabase.ansattArrangorDbo(
            arrangorId = arrangorOne.id,
            koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerliste)),
        )
        val ansatt = testDatabase.insertAnsatt(arrangorer = listOf(arrangor))

        ansattService.fjernTilgangerHosArrangor(deltakerliste, emptyList(), arrangor.arrangorId)
        val ansattInDb = hentAnsattMedArrangorer(ansatt.id)
        ansattInDb.shouldNotBeNull()

        ansattInDb
            .arrangorer[0]
            .koordinator[0]
            .gyldigTil shouldNotBe null
    }

    @Test
    fun `fjernTilgangerHosArrangor - ansatt er koordinator for flere lister hos arrangor - riktig tilgang skal fjernes`() {
        val deltakerliste1 = UUID.randomUUID()
        val deltakerliste2 = UUID.randomUUID()

        val arrangor =
            testDatabase.ansattArrangorDbo(
                arrangorId = arrangorOne.id,
                koordinator =
                    listOf(
                        KoordinatorsDeltakerlisteDbo(deltakerliste1),
                        KoordinatorsDeltakerlisteDbo(deltakerliste2),
                    ),
            )
        val ansatt = testDatabase.insertAnsatt(arrangorer = listOf(arrangor))

        ansattService.fjernTilgangerHosArrangor(deltakerliste1, emptyList(), arrangor.arrangorId)

        val ansattInDb = hentAnsattMedArrangorer(ansatt.id)
        ansattInDb.shouldNotBeNull()

        val ansattArrangorTilganger = ansattInDb.arrangorer.first { it.arrangorId == arrangor.arrangorId }
        ansattArrangorTilganger.koordinator.first { it.deltakerlisteId == deltakerliste1 }.erGyldig() shouldBe false
        ansattArrangorTilganger.koordinator.first { it.deltakerlisteId == deltakerliste2 }.erGyldig() shouldBe true
    }

    @Test
    fun `fjernTilgangerHosArrangor - veileder skal fjernes`() {
        val deltaker = testDatabase.insertDeltaker()
        val arrangor =
            testDatabase.ansattArrangorDbo(
                arrangorId = arrangorOne.id,
                roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                veileder = listOf(VeilederDeltakerDbo(deltaker, VeilederType.VEILEDER)),
            )
        val ansatt = testDatabase.insertAnsatt(arrangorer = listOf(arrangor))

        ansattService.fjernTilgangerHosArrangor(UUID.randomUUID(), listOf(deltaker), arrangor.arrangorId)
        val ansattInDb = hentAnsattMedArrangorer(ansatt.id)
        ansattInDb.shouldNotBeNull()

        ansattInDb
            .arrangorer[0]
            .veileder[0]
            .gyldigTil shouldNotBe null
    }

    @Test
    fun `fjernTilgangerHosArrangor - veileder har flere tilganger hos arrangor - kun tilgang til gitte deltakere skal fjernes`() {
        val deltaker1 = testDatabase.insertDeltaker()
        val deltaker2 = testDatabase.insertDeltaker()
        val deltaker3 = UUID.randomUUID()

        val arrangor1 =
            testDatabase.ansattArrangorDbo(
                arrangorId = arrangorOne.id,
                roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                veileder =
                    listOf(
                        VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER),
                        VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER),
                    ),
            )
        val arrangor2 =
            testDatabase.ansattArrangorDbo(
                arrangorId = arrangorTwo.id,
                roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                veileder =
                    listOf(
                        VeilederDeltakerDbo(deltaker3, VeilederType.VEILEDER),
                    ),
            )
        val ansatt = testDatabase.insertAnsatt(arrangorer = listOf(arrangor1, arrangor2))

        ansattService.fjernTilgangerHosArrangor(UUID.randomUUID(), listOf(deltaker1, deltaker2), arrangor1.arrangorId)

        val ansattInDb = hentAnsattMedArrangorer(ansatt.id)
        ansattInDb.shouldNotBeNull()
        val ansattArrangor1Tilganger = ansattInDb.arrangorer.first { it.arrangorId == arrangor1.arrangorId }

        ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker1 }.erGyldig() shouldBe false
        ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker2 }.erGyldig() shouldBe false

        val ansattArrangor2Tilganger = ansattInDb.arrangorer.first { it.arrangorId == arrangor2.arrangorId }
        ansattArrangor2Tilganger.veileder.first { it.deltakerId == deltaker3 }.erGyldig() shouldBe true
    }

    @Test
    fun `fjernTilgangerHosArrangor - ansatt er koordinator og veileder hos arrangor - fjerner riktige tilganger`() {
        // Arrange
        val deltaker1 = testDatabase.insertDeltaker()

        val deltakerliste = UUID.randomUUID()

        val arrangor1 =
            testDatabase.ansattArrangorDbo(
                arrangorId = arrangorOne.id,
                roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR), RolleDbo(AnsattRolle.VEILEDER)),
                veileder =
                    listOf(
                        VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER),
                    ),
                koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerliste)),
            )
        val ansatt = testDatabase.insertAnsatt(arrangorer = listOf(arrangor1))
        ansattArrangorRepository.replaceForAnsatt(ansatt.id, ansatt.arrangorer)

        // Act
        ansattService.fjernTilgangerHosArrangor(deltakerliste, listOf(deltaker1), arrangor1.arrangorId)

        // Assert
        val ansattInDb = hentAnsattMedArrangorer(ansatt.id)
        ansattInDb.shouldNotBeNull()

        val ansattArrangor1Tilganger = ansattInDb.arrangorer.first { it.arrangorId == arrangor1.arrangorId }
        ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker1 }.erGyldig() shouldBe false
        ansattArrangor1Tilganger.koordinator.first { it.deltakerlisteId == deltakerliste }.erGyldig() shouldBe false
        val veilederFjerningstidspunkt = ansattArrangor1Tilganger.veileder
            .first { it.deltakerId == deltaker1 }
            .gyldigTil
            .shouldNotBeNull()
            .toInstant()
        val koordinatorFjerningstidspunkt = ansattArrangor1Tilganger.koordinator
            .first { it.deltakerlisteId == deltakerliste }
            .gyldigTil
            .shouldNotBeNull()
            .toInstant()
        veilederFjerningstidspunkt shouldBe koordinatorFjerningstidspunkt
        val normaliserteTilganger = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id).single()
        normaliserteTilganger.veileder.single().erGyldig() shouldBe false
        normaliserteTilganger.koordinator.single().erGyldig() shouldBe false
    }

    @Test
    fun `setKoordinator - ny deltakerliste - ny tilgang opprettes`() {
        val ansatt =
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
        val deltakerlisteId = UUID.randomUUID()

        ansattService.setKoordinatorForDeltakerliste(ansatt.personident, arrangorOne.id, deltakerlisteId)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansatt.id)
        val koordinator = oppdatertAnsatt?.arrangorer?.first()?.koordinator

        assertSoftly(koordinator.shouldNotBeNull()) {
            size shouldBe 1
            first().deltakerlisteId shouldBe deltakerlisteId
            first().erGyldig() shouldBe true
        }
        ansattArrangorRepository
            .getArrangorerForAnsatt(ansatt.id)
            .single()
            .koordinator
            .single()
            .let {
                it.deltakerlisteId shouldBe deltakerlisteId
                it.erGyldig() shouldBe true
            }
    }

    @Test
    fun `setKoordinator - aktiv tilgang for deltakerliste finnes - tilgang opprettes ikke`() {
        val deltakerlisteId = UUID.randomUUID()
        val ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerlisteId)),
                        ),
                    ),
            )

        ansattService.setKoordinatorForDeltakerliste(ansatt.personident, arrangorOne.id, deltakerlisteId)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansatt.id)
        val koordinator = oppdatertAnsatt?.arrangorer?.first()?.koordinator

        assertSoftly(koordinator.shouldNotBeNull()) {
            size shouldBe 1
            first().deltakerlisteId shouldBe deltakerlisteId
            first().erGyldig() shouldBe true
        }
    }

    @Test
    fun `setKoordinator - deaktivert tilgang for deltakerliste finnes - ny tilgang opprettes`() {
        val deltakerlisteId = UUID.randomUUID()
        val ansatt =
            testDatabase.insertAnsatt(
                arrangorer =
                    listOf(
                        ArrangorDbo(
                            arrangorId = arrangorOne.id,
                            roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                            veileder = emptyList(),
                            koordinator = listOf(
                                KoordinatorsDeltakerlisteDbo(
                                    deltakerlisteId,
                                    gyldigTil = ZonedDateTime.now().minusDays(8),
                                ),
                            ),
                        ),
                    ),
            )

        ansattService.setKoordinatorForDeltakerliste(ansatt.personident, arrangorOne.id, deltakerlisteId)

        val oppdatertAnsatt = hentAnsattMedArrangorer(ansatt.id)
        val koordinator = oppdatertAnsatt?.arrangorer?.first()?.koordinator

        assertSoftly(koordinator.shouldNotBeNull()) { deltakerlisteDbos ->
            size shouldBe 2
            deltakerlisteDbos[0].deltakerlisteId shouldBe deltakerlisteId
            deltakerlisteDbos[0].deltakerlisteId shouldBe deltakerlisteId
            deltakerlisteDbos[1].deltakerlisteId shouldBe deltakerlisteId
            deltakerlisteDbos.any { it.erGyldig() } shouldBe true
            deltakerlisteDbos.any { !it.erGyldig() } shouldBe true
        }
    }

    private fun hentAnsattMedArrangorer(id: UUID) = ansattRepository.get(id)
}
