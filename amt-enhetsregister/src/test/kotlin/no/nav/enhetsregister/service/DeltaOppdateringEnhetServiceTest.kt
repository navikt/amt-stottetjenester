package no.nav.enhetsregister.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.enhetsregister.client.BronnoysundClient
import no.nav.enhetsregister.domain.EnhetOppdatering
import no.nav.enhetsregister.domain.EnhetOppdateringType
import no.nav.enhetsregister.domain.EnhetType
import no.nav.enhetsregister.domain.Moderenhet
import no.nav.enhetsregister.domain.Underenhet
import no.nav.enhetsregister.repository.DeltaOppdateringProgresjonRepository
import no.nav.enhetsregister.repository.model.DeltaEnhetOppdateringProgresjon
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class DeltaOppdateringEnhetServiceTest {
    val bronnoysundClient: BronnoysundClient = mockk()
    val enhetService: EnhetService = mockk()
    val deltaOppdateringProgresjonRepository: DeltaOppdateringProgresjonRepository = mockk()
    val deltaOppdateringEnhetService =
        DeltaOppdateringEnhetService(
            enhetService = enhetService,
            deltaOppdateringRepository = deltaOppdateringProgresjonRepository,
            bronnoysundClient = bronnoysundClient,
        )

    @BeforeEach
    fun setupMock() {
        every {
            deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.MODERENHET)
        } returns DeltaEnhetOppdateringProgresjon(
            oppdateringId = 33,
            enhetType = EnhetType.MODERENHET,
        )

        every {
            deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.UNDERENHET)
        } returns DeltaEnhetOppdateringProgresjon(
            oppdateringId = 33,
            enhetType = EnhetType.UNDERENHET,
        )

        every { enhetService.oppdaterEnheter(any()) } returns Unit
        every {
            deltaOppdateringProgresjonRepository.oppdaterProgresjon(any(), any())
        } returns Unit
    }

    companion object {
        private val enhetOppdateringInTest =
            EnhetOppdatering(
                oppdateringId = 33,
                dato = ZonedDateTime.now(),
                organisasjonsnummer = "999000000",
                endringstype = EnhetOppdateringType.ENDRING,
            )

        private val moderEnhetInTest = Moderenhet(
            organisasjonsnummer = "999000000",
            navn = "Nytt Orgnavn",
            slettedato = null,
        )

        private val upsertEnhetInTest = EnhetService.UpsertEnhet(
            organisasjonsnummer = "999000000",
            navn = "Nytt Orgnavn",
            overordnetEnhetOrgNr = null,
        )

        private val underEnhetInTest = Underenhet(
            organisasjonsnummer = "899000000",
            navn = "Nytt Orgnavn",
            slettedato = null,
            overordnetEnhet = "999000000",
        )

        private val upsertUnderEnhetInTest = EnhetService.UpsertEnhet(
            organisasjonsnummer = "899000000",
            navn = "Nytt Orgnavn",
            overordnetEnhetOrgNr = "999000000",
        )
    }

    // Moderenheter
    @Test
    fun `endret moderenhet skal endres`() {
        every {
            bronnoysundClient.hentModerenhetOppdateringer(any(), any())
        } returns listOf(enhetOppdateringInTest)

        every { bronnoysundClient.hentModerenhet("999000000") } returns moderEnhetInTest

        deltaOppdateringEnhetService.deltaOppdaterModerenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(listOf(upsertEnhetInTest))
        }
    }

    @Test
    fun `ny moderenhet skal upsertes`() {
        every {
            bronnoysundClient.hentModerenhetOppdateringer(any(), any())
        } returns
            listOf(
                enhetOppdateringInTest.copy(endringstype = EnhetOppdateringType.NY),
            )

        every {
            bronnoysundClient.hentModerenhet("999000000")
        } returns moderEnhetInTest

        deltaOppdateringEnhetService.deltaOppdaterModerenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(listOf(upsertEnhetInTest))
        }
    }

    @Test
    fun `ukjent endring av moderenhet skal upsertes`() {
        every {
            bronnoysundClient.hentModerenhetOppdateringer(any(), any())
        } returns listOf(enhetOppdateringInTest.copy(endringstype = EnhetOppdateringType.UKJENT))

        every {
            bronnoysundClient.hentModerenhet("999000000")
        } returns moderEnhetInTest

        deltaOppdateringEnhetService.deltaOppdaterModerenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(listOf(upsertEnhetInTest))
        }
    }

    @Test
    fun `sletting av moderenhet skal gi slettet i navnet `() {
        every {
            bronnoysundClient.hentModerenhetOppdateringer(any(), any())
        } returns listOf(enhetOppdateringInTest.copy(endringstype = EnhetOppdateringType.SLETTING))

        every {
            bronnoysundClient.hentModerenhet("999000000")
        } returns moderEnhetInTest.copy(slettedato = ZonedDateTime.now().toString())

        deltaOppdateringEnhetService.deltaOppdaterModerenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    upsertEnhetInTest.copy(navn = "Nytt Orgnavn (slettet)"),
                ),
            )
        }
    }

    @Test
    fun `fjerning av moderenhet skal gi fjernet navn `() {
        every {
            bronnoysundClient.hentModerenhetOppdateringer(any(), any())
        } returns listOf(enhetOppdateringInTest.copy(endringstype = EnhetOppdateringType.FJERNET))

        every {
            bronnoysundClient.hentModerenhet("999000000")
        } returns null

        deltaOppdateringEnhetService.deltaOppdaterModerenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    upsertEnhetInTest.copy(navn = "Fjernet virksomhet"),
                ),
            )
        }
    }

    // Samme for underenheter

    @Test
    fun `endret underenhet skal endres`() {
        every {
            bronnoysundClient.hentUnderenhetOppdateringer(any(), any())
        } returns listOf(enhetOppdateringInTest)

        every {
            bronnoysundClient.hentUnderenhet("999000000")
        } returns underEnhetInTest

        deltaOppdateringEnhetService.deltaOppdaterUnderenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(listOf(upsertUnderEnhetInTest))
        }
    }

    @Test
    fun `ny underenhet skal upsertes`() {
        every {
            bronnoysundClient.hentUnderenhetOppdateringer(any(), any())
        } returns listOf(
            EnhetOppdatering(
                oppdateringId = 33,
                dato = ZonedDateTime.now(),
                organisasjonsnummer = "899000000",
                endringstype = EnhetOppdateringType.NY,
            ),
        )

        every {
            bronnoysundClient.hentUnderenhet("899000000")
        } returns Underenhet(
            organisasjonsnummer = "899000000",
            navn = "Orgnavn",
            slettedato = null,
            overordnetEnhet = "999000000",
        )

        deltaOppdateringEnhetService.deltaOppdaterUnderenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    EnhetService.UpsertEnhet(
                        organisasjonsnummer = "899000000",
                        navn = "Orgnavn",
                        overordnetEnhetOrgNr = "999000000",
                    ),
                ),
            )
        }
    }

    @Test
    fun `ukjent endring av underenhet skal upsertes`() {
        every {
            bronnoysundClient.hentUnderenhetOppdateringer(any(), any())
        } returns listOf(
            EnhetOppdatering(
                oppdateringId = 33,
                dato = ZonedDateTime.now(),
                organisasjonsnummer = "899000000",
                endringstype = EnhetOppdateringType.UKJENT,
            ),
        )

        every {
            bronnoysundClient.hentUnderenhet("899000000")
        } returns Underenhet(
            organisasjonsnummer = "899000000",
            navn = "Orgnavn",
            slettedato = null,
            overordnetEnhet = "999000000",
        )

        deltaOppdateringEnhetService.deltaOppdaterUnderenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    EnhetService.UpsertEnhet(
                        organisasjonsnummer = "899000000",
                        navn = "Orgnavn",
                        overordnetEnhetOrgNr = "999000000",
                    ),
                ),
            )
        }
    }

    @Test
    fun `sletting av underenhet skal gi slettet i navnet `() {
        every {
            bronnoysundClient.hentUnderenhetOppdateringer(any(), any())
        } returns listOf(
            EnhetOppdatering(
                oppdateringId = 33,
                dato = ZonedDateTime.now(),
                organisasjonsnummer = "899000000",
                endringstype = EnhetOppdateringType.SLETTING,
            ),
        )

        every {
            bronnoysundClient.hentUnderenhet("899000000")
        } returns Underenhet(
            organisasjonsnummer = "899000000",
            navn = "Orgnavn",
            slettedato = ZonedDateTime.now().toString(),
            overordnetEnhet = null,
        )

        every {
            enhetService.hentEnhet("899000000")
        } returns EnhetService.EnhetMedOverordnetEnhet(
            organisasjonsnummer = "899000000",
            navn = "Orgnavn",
            overordnetEnhetOrganisasjonsnummer = "999000000",
            overordnetEnhetNavn = "Moderenhetnavn",
        )

        deltaOppdateringEnhetService.deltaOppdaterUnderenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    EnhetService.UpsertEnhet(
                        organisasjonsnummer = "899000000",
                        navn = "Orgnavn (slettet)",
                        overordnetEnhetOrgNr = "999000000",
                    ),
                ),
            )
        }
    }

    @Test
    fun `fjerning av underenhet skal gi fjernet navn `() {
        every {
            bronnoysundClient.hentUnderenhetOppdateringer(any(), any())
        } returns listOf(
            EnhetOppdatering(
                oppdateringId = 33,
                dato = ZonedDateTime.now(),
                organisasjonsnummer = "899000000",
                endringstype = EnhetOppdateringType.FJERNET,
            ),
        )

        every {
            bronnoysundClient.hentUnderenhet("899000000")
        } returns null

        every {
            enhetService.hentEnhet("899000000")
        } returns EnhetService.EnhetMedOverordnetEnhet(
            organisasjonsnummer = "899000000",
            navn = "Orgnavn",
            overordnetEnhetOrganisasjonsnummer = "999000000",
            overordnetEnhetNavn = "Moderenhetnavn",
        )

        deltaOppdateringEnhetService.deltaOppdaterUnderenheter()

        verify(exactly = 1) {
            enhetService.oppdaterEnheter(
                listOf(
                    EnhetService.UpsertEnhet(
                        organisasjonsnummer = "899000000",
                        navn = "Fjernet virksomhet",
                        overordnetEnhetOrgNr = "999000000",
                    ),
                ),
            )
        }
    }
}
