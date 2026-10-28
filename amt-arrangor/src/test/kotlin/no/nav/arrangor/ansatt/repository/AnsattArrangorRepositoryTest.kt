package no.nav.arrangor.ansatt.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class AnsattArrangorRepositoryTest(
    private val ansattArrangorRepository: AnsattArrangorRepository,
) : RepositoryTestBase() {
    @Nested
    inner class ReplaceForAnsatt {
        @Test
        fun `replaceForAnsatt - ny ansatt - setter inn rader i alle fire tabeller`() {
            // Arrange
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()
            val deltakerId = testDatabase.insertDeltaker()
            val deltakerlisteId = UUID.randomUUID()

            val arrangorer = listOf(
                ArrangorDbo(
                    arrangorId = arrangor.id,
                    roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                    veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
                    koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerlisteId)),
                ),
            )

            // Act
            ansattArrangorRepository.replaceForAnsatt(ansatt.id, arrangorer)

            // Assert
            val lagret = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)
            lagret shouldHaveSize 1
            assertSoftly(lagret.first()) {
                arrangorId shouldBe arrangor.id

                roller shouldHaveSize 1
                roller.first().rolle shouldBe AnsattRolle.VEILEDER

                veileder shouldHaveSize 1
                veileder.first().deltakerId shouldBe deltakerId
                veileder.first().veilederType shouldBe VeilederType.MEDVEILEDER

                koordinator shouldHaveSize 1
                koordinator.first().deltakerlisteId shouldBe deltakerlisteId
            }
        }

        @Test
        fun `replaceForAnsatt - bevarer gyldigFra og gyldigTil`() {
            // Arrange
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()
            val gyldigFra = ZonedDateTime.now().minusDays(7)
            val gyldigTil = ZonedDateTime.now().plusDays(7)

            val arrangorer = listOf(
                ArrangorDbo(
                    arrangorId = arrangor.id,
                    roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigFra, gyldigTil)),
                    veileder = emptyList(),
                    koordinator = emptyList(),
                ),
            )

            // Act
            ansattArrangorRepository.replaceForAnsatt(ansatt.id, arrangorer)

            // Assert
            val lagretRolle = ansattArrangorRepository
                .getArrangorerForAnsatt(ansatt.id)
                .first()
                .roller
                .first()
            assertSoftly(lagretRolle) {
                it.gyldigFra shouldBeCloseTo gyldigFra
                it.gyldigTil.shouldNotBeNull().shouldBeCloseTo(gyldigTil)
            }
        }

        @Test
        fun `replaceForAnsatt - gyldigTil er null - forblir null`() {
            // Arrange
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()

            // Act
            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.VEILEDER, gyldigTil = null)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )

            // Assert
            ansattArrangorRepository
                .getArrangorerForAnsatt(ansatt.id)
                .first()
                .roller
                .first()
                .gyldigTil
                .shouldBeNull()
        }

        @Test
        fun `replaceForAnsatt - kalles pa nytt - erstatter tidligere tilstand`() {
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor1 = testDatabase.insertArrangor()
            val arrangor2 = testDatabase.insertArrangor()
            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor1.id,
                        roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )

            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor2.id,
                        roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )

            val lagret = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)
            lagret shouldHaveSize 1
            lagret.single().arrangorId shouldBe arrangor2.id
            lagret
                .single()
                .roller
                .single()
                .rolle shouldBe AnsattRolle.KOORDINATOR
        }

        @Test
        fun `replaceForAnsatt - flere arrangorer og flere roller - lagrer alle`() {
            // Arrange
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor1 = testDatabase.insertArrangor()
            val arrangor2 = testDatabase.insertArrangor()

            val arrangorer = listOf(
                ArrangorDbo(
                    arrangorId = arrangor1.id,
                    roller = listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
                    veileder = emptyList(),
                    koordinator = emptyList(),
                ),
                ArrangorDbo(
                    arrangorId = arrangor2.id,
                    roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                    veileder = emptyList(),
                    koordinator = emptyList(),
                ),
            )

            // Act
            ansattArrangorRepository.replaceForAnsatt(ansatt.id, arrangorer)

            // Assert
            val lagret = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id)
            assertSoftly {
                lagret shouldHaveSize 2
                lagret.first { it.arrangorId == arrangor1.id }.roller shouldHaveSize 2
                lagret.first { it.arrangorId == arrangor2.id }.roller shouldHaveSize 1
            }
        }
    }

    @Nested
    inner class OperationSpecificWrites {
        @Test
        fun `rolleoperasjoner endrer roller uten a overskrive tilganger`() {
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()
            val deltakerId = testDatabase.insertDeltaker()
            val deltakerlisteId = UUID.randomUUID()
            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangor.id,
                        listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        listOf(VeilederDeltakerDbo(deltakerId, VeilederType.VEILEDER)),
                        listOf(KoordinatorsDeltakerlisteDbo(deltakerlisteId)),
                    ),
                ),
            )

            val gammelRolle = ansattArrangorRepository
                .getArrangorerForAnsatt(ansatt.id)
                .single()
                .roller
                .single()
            gammelRolle.gyldigTil = ZonedDateTime.now()
            ansattArrangorRepository.deaktiverRolle(ansatt.id, arrangor.id, gammelRolle)
            val nyRolle = RolleDbo(AnsattRolle.KOORDINATOR)
            ansattArrangorRepository.insertRolle(ansatt.id, arrangor.id, nyRolle)
            ansattArrangorRepository.insertRolle(ansatt.id, arrangor.id, nyRolle)

            val lagret = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id).single()
            lagret.roller shouldHaveSize 2
            lagret.roller
                .first { it.rolle == AnsattRolle.VEILEDER }
                .gyldigTil
                .shouldNotBeNull()
            lagret.roller
                .first { it.rolle == AnsattRolle.KOORDINATOR }
                .gyldigTil
                .shouldBeNull()
            lagret.veileder.single().deltakerId shouldBe deltakerId
            lagret.koordinator.single().deltakerlisteId shouldBe deltakerlisteId
        }

        @Test
        fun `insert og deaktiver veileder bruker den konkrete koblingen`() {
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()
            val veileder = VeilederDeltakerDbo(testDatabase.insertDeltaker(), VeilederType.MEDVEILEDER)
            ansattArrangorRepository.insertVeileder(ansatt.id, arrangor.id, veileder)
            ansattArrangorRepository.insertVeileder(ansatt.id, arrangor.id, veileder)
            val gyldigTil = ZonedDateTime.now().plusDays(1)

            ansattArrangorRepository.deaktiverVeileder(
                ansatt.id,
                arrangor.id,
                veileder.apply { this.gyldigTil = gyldigTil },
            )

            ansattArrangorRepository
                .getArrangorerForAnsatt(ansatt.id)
                .single()
                .veileder
                .single()
                .gyldigTil
                .shouldNotBeNull()
                .shouldBeCloseTo(gyldigTil)
        }

        @Test
        fun `insert og deaktiver koordinator bruker den konkrete koblingen`() {
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val arrangor = testDatabase.insertArrangor()
            val koordinator = KoordinatorsDeltakerlisteDbo(UUID.randomUUID())
            ansattArrangorRepository.insertKoordinator(ansatt.id, arrangor.id, koordinator)
            ansattArrangorRepository.insertKoordinator(ansatt.id, arrangor.id, koordinator)
            val gyldigTil = ZonedDateTime.now().plusDays(1)

            ansattArrangorRepository.deaktiverKoordinator(
                ansatt.id,
                arrangor.id,
                koordinator.apply { this.gyldigTil = gyldigTil },
            )

            ansattArrangorRepository
                .getArrangorerForAnsatt(ansatt.id)
                .single()
                .koordinator
                .single()
                .gyldigTil
                .shouldNotBeNull()
                .shouldBeCloseTo(gyldigTil)
        }
    }

    @Nested
    inner class DeaktiverVeiledereForDeltaker {
        @Test
        fun `deaktiverVeiledereForDeltaker - aktive veiledere - deaktiverer alle aktive veiledere for deltaker`() {
            // Arrange
            val deltaker1 = testDatabase.insertDeltaker()
            val deltaker2 = testDatabase.insertDeltaker()
            val arrangor = testDatabase.insertArrangor()

            val ansatt1 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansatt2 = testDatabase.insertAnsatt(arrangorer = emptyList())

            ansattArrangorRepository.replaceForAnsatt(
                ansatt1.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        veileder = listOf(
                            VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER),
                            VeilederDeltakerDbo(deltaker2, VeilederType.MEDVEILEDER),
                        ),
                        koordinator = emptyList(),
                    ),
                ),
            )
            ansattArrangorRepository.replaceForAnsatt(
                ansatt2.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        veileder = listOf(
                            VeilederDeltakerDbo(deltaker1, VeilederType.MEDVEILEDER),
                            VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER),
                        ),
                        koordinator = emptyList(),
                    ),
                ),
            )

            val deaktiveringsdato = ZonedDateTime.now().plusDays(1)

            // Act
            val beroerteAnsatte = ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltaker1, deaktiveringsdato)

            // Assert
            beroerteAnsatte shouldContainExactlyInAnyOrder listOf(ansatt1.id, ansatt2.id)

            assertSoftly {
                val ansatt1Etter = ansattArrangorRepository.getArrangorerForAnsatt(ansatt1.id).first()
                ansatt1Etter.veileder
                    .first { it.deltakerId == deltaker1 }
                    .gyldigTil
                    .shouldNotBeNull()
                    .shouldBeCloseTo(deaktiveringsdato)
                ansatt1Etter.veileder
                    .first { it.deltakerId == deltaker2 }
                    .gyldigTil
                    .shouldBeNull()

                val ansatt2Etter = ansattArrangorRepository.getArrangorerForAnsatt(ansatt2.id).first()
                ansatt2Etter.veileder
                    .first { it.deltakerId == deltaker1 }
                    .gyldigTil
                    .shouldNotBeNull()
                    .shouldBeCloseTo(deaktiveringsdato)
                ansatt2Etter.veileder
                    .first { it.deltakerId == deltaker2 }
                    .gyldigTil
                    .shouldBeNull()
            }
        }

        @Test
        fun `deaktiverVeiledereForDeltaker - allerede deaktivert - endres ikke`() {
            // Arrange
            val deltaker = testDatabase.insertDeltaker()
            val arrangor = testDatabase.insertArrangor()
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val opprinneligGyldigTil = ZonedDateTime.now().minusDays(1)

            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = emptyList(),
                        veileder = listOf(VeilederDeltakerDbo(deltaker, VeilederType.VEILEDER, gyldigTil = opprinneligGyldigTil)),
                        koordinator = emptyList(),
                    ),
                ),
            )

            // Act
            val beroerte = ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltaker, ZonedDateTime.now())

            // Assert
            assertSoftly {
                beroerte.shouldBeEmpty()

                ansattArrangorRepository
                    .getArrangorerForAnsatt(ansatt.id)
                    .first()
                    .veileder
                    .first()
                    .gyldigTil
                    .shouldNotBeNull()
                    .shouldBeCloseTo(opprinneligGyldigTil)
            }
        }
    }

    @Nested
    inner class MaybeReaktiverVeiledereForDeltaker {
        @Test
        fun `maybeReaktiverVeiledereForDeltaker - reaktiverer gyldige veiledere for deltaker`() {
            // Arrange
            val deltaker1 = testDatabase.insertDeltaker()
            val deltaker2 = testDatabase.insertDeltaker()
            val arrangor = testDatabase.insertArrangor()

            val ansatt1 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansatt2 = testDatabase.insertAnsatt(arrangorer = emptyList())

            ansattArrangorRepository.replaceForAnsatt(
                ansatt1.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = emptyList(),
                        veileder = listOf(
                            VeilederDeltakerDbo(
                                deltaker1,
                                VeilederType.VEILEDER,
                                ZonedDateTime.now().minusDays(2),
                                ZonedDateTime.now().plusDays(2),
                            ),
                            VeilederDeltakerDbo(deltaker2, VeilederType.MEDVEILEDER),
                        ),
                        koordinator = emptyList(),
                    ),
                ),
            )
            ansattArrangorRepository.replaceForAnsatt(
                ansatt2.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = emptyList(),
                        veileder = listOf(
                            VeilederDeltakerDbo(
                                deltaker1,
                                VeilederType.MEDVEILEDER,
                                ZonedDateTime.now().minusDays(3),
                                ZonedDateTime.now().plusDays(3),
                            ),
                            VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER),
                        ),
                        koordinator = emptyList(),
                    ),
                ),
            )

            // Act
            val beroerte = ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltaker1)

            // Assert
            assertSoftly {
                beroerte shouldContainExactlyInAnyOrder listOf(ansatt1.id, ansatt2.id)

                listOf(ansatt1, ansatt2).forEach { ansatt ->
                    val etter = ansattArrangorRepository.getArrangorerForAnsatt(ansatt.id).first()
                    etter.veileder
                        .first { it.deltakerId == deltaker1 }
                        .gyldigTil
                        .shouldBeNull()
                    etter.veileder
                        .first { it.deltakerId == deltaker2 }
                        .gyldigTil
                        .shouldBeNull()
                }
            }
        }

        @Test
        fun `maybeReaktiverVeiledereForDeltaker - gyldigTil er i fortiden - reaktiverer ikke`() {
            // Arrange
            val deltaker = testDatabase.insertDeltaker()
            val arrangor = testDatabase.insertArrangor()
            val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
            val gyldigTilIFortiden = ZonedDateTime.now().minusDays(1)

            ansattArrangorRepository.replaceForAnsatt(
                ansatt.id,
                listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = emptyList(),
                        veileder = listOf(VeilederDeltakerDbo(deltaker, VeilederType.VEILEDER, gyldigTil = gyldigTilIFortiden)),
                        koordinator = emptyList(),
                    ),
                ),
            )

            // Act
            val beroerte = ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltaker)

            // Assert
            assertSoftly {
                beroerte.shouldBeEmpty()

                ansattArrangorRepository
                    .getArrangorerForAnsatt(ansatt.id)
                    .first()
                    .veileder
                    .first()
                    .gyldigTil
                    .shouldNotBeNull()
                    .shouldBeCloseTo(gyldigTilIFortiden)
            }
        }
    }

    @Nested
    inner class GetForAnsatt {
        @Test
        fun `getForAnsatt - ingen data - returnerer tom liste`() {
            // Arrange (ingen data satt opp)

            // Act
            val resultat = ansattArrangorRepository.getArrangorerForAnsatt(UUID.randomUUID())

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `getForAnsatte - flere ansatte - returnerer komplett tilstand gruppert per ansatt`() {
            val arrangor1 = testDatabase.insertArrangor()
            val arrangor2 = testDatabase.insertArrangor()
            val ansatt1 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansatt2 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansattUtenTilknytning = testDatabase.insertAnsatt(arrangorer = emptyList())
            val deltakerId = testDatabase.insertDeltaker()
            val deltakerlisteId = UUID.randomUUID()

            ansattArrangorRepository.replaceForAnsatt(
                ansatt1.id,
                listOf(
                    ArrangorDbo(
                        arrangor1.id,
                        listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
                        listOf(KoordinatorsDeltakerlisteDbo(deltakerlisteId)),
                    ),
                ),
            )
            ansattArrangorRepository.replaceForAnsatt(
                ansatt2.id,
                listOf(
                    ArrangorDbo(
                        arrangor2.id,
                        listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                        emptyList(),
                        emptyList(),
                    ),
                ),
            )

            val resultat = ansattArrangorRepository.getArrangorerForAnsatte(
                listOf(ansatt1.id, ansatt2.id, ansattUtenTilknytning.id),
            )

            resultat[ansatt1.id].shouldNotBeNull().single().let {
                it.arrangorId shouldBe arrangor1.id
                it.roller.single().rolle shouldBe AnsattRolle.VEILEDER
                it.veileder.single().deltakerId shouldBe deltakerId
                it.koordinator.single().deltakerlisteId shouldBe deltakerlisteId
            }
            resultat[ansatt2.id].shouldNotBeNull().single().let {
                it.arrangorId shouldBe arrangor2.id
                it.roller.single().rolle shouldBe AnsattRolle.KOORDINATOR
            }
            resultat[ansattUtenTilknytning.id].shouldNotBeNull().shouldBeEmpty()
        }

        @Test
        fun `getAnsattIderForArrangor - returnerer bare ansatte hos valgt arrangor`() {
            val arrangor1 = testDatabase.insertArrangor()
            val arrangor2 = testDatabase.insertArrangor()
            val ansatt1 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansatt2 = testDatabase.insertAnsatt(arrangorer = emptyList())
            val ansattHosAnnenArrangor = testDatabase.insertAnsatt(arrangorer = emptyList())
            ansattArrangorRepository.insertRolle(ansatt1.id, arrangor1.id, RolleDbo(AnsattRolle.VEILEDER))
            ansattArrangorRepository.insertRolle(ansatt2.id, arrangor1.id, RolleDbo(AnsattRolle.KOORDINATOR))
            ansattArrangorRepository.insertRolle(
                ansattHosAnnenArrangor.id,
                arrangor2.id,
                RolleDbo(AnsattRolle.VEILEDER),
            )

            ansattArrangorRepository.getAnsattIderForArrangor(arrangor1.id) shouldContainExactlyInAnyOrder
                listOf(ansatt1.id, ansatt2.id)
        }
    }
}
