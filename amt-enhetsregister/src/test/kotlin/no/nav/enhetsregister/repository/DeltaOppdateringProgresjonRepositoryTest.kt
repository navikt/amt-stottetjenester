package no.nav.enhetsregister.repository

import no.nav.enhetsregister.domain.EnhetType
import no.nav.enhetsregister.testutils.DatabaseTestUtils.cleanAndInitDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DeltaOppdateringProgresjonRepository::class])
class DeltaOppdateringProgresjonRepositoryTest(
    private val deltaOppdateringProgresjonRepository: DeltaOppdateringProgresjonRepository,
) : RepositoryTestBase() {
    @BeforeEach
    fun setUp() = cleanAndInitDatabase(dataSource, "/db/delta-enhet-oppdatering.sql")

    @Test
    fun `hentOppdateringProgresjon skal hente oppdatering progresjon for moderenhet`() {
        val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.MODERENHET)

        assertEquals(1234, progresjon.oppdateringId)
        assertEquals(EnhetType.MODERENHET, progresjon.enhetType)
    }

    @Test
    fun `hentOppdateringProgresjon skal hente oppdatering progresjon for underenhet`() {
        val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.UNDERENHET)

        assertEquals(5678, progresjon.oppdateringId)
        assertEquals(EnhetType.UNDERENHET, progresjon.enhetType)
    }

    @Test
    fun `oppdaterProgresjon skal oppdatere oppdateringsid`() {
        deltaOppdateringProgresjonRepository.oppdaterProgresjon(EnhetType.MODERENHET, 5)

        val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.MODERENHET)

        assertEquals(5, progresjon.oppdateringId)
    }
}
