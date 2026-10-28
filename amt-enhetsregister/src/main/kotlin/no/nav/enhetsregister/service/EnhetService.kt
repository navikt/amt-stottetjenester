package no.nav.enhetsregister.service

import no.nav.enhetsregister.client.BronnoysundClient
import no.nav.enhetsregister.kafka.VirksomhetV1Dto
import no.nav.enhetsregister.repository.EnhetRepository
import no.nav.enhetsregister.repository.command.UpsertEnhetCmd
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class EnhetService(
    private val enhetRepository: EnhetRepository,
    private val bronnoysundClient: BronnoysundClient,
    private val kafkaProducerService: KafkaProducerService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hentEnhet(organisasjonsnummer: String): EnhetMedOverordnetEnhet? {
        val enhet = enhetRepository.hentEnhet(organisasjonsnummer)
            ?: return fallbackTilBronnoysundOgOppdaterEnhetHvisMangler(organisasjonsnummer)

        val overordnetEnhetNavn = enhet.overordnetEnhet
            ?.let { enhetRepository.hentEnhet(it)?.navn }

        return EnhetMedOverordnetEnhet(
            organisasjonsnummer = enhet.organisasjonsnummer,
            navn = enhet.navn,
            overordnetEnhetOrganisasjonsnummer = enhet.overordnetEnhet,
            overordnetEnhetNavn = overordnetEnhetNavn,
        )
    }

    fun fallbackTilBronnoysundOgOppdaterEnhetHvisMangler(organisasjonsnummer: String): EnhetMedOverordnetEnhet? {
        val underEnhet = bronnoysundClient.hentUnderenhet(organisasjonsnummer)
        if (underEnhet != null) {
            log.info("Fant manglende underenhet orgnr=$organisasjonsnummer i brreg : $underEnhet ")
            oppdaterEnheter(
                listOf(
                    UpsertEnhet(
                        organisasjonsnummer = organisasjonsnummer,
                        navn = "${underEnhet.navn}${if (underEnhet.slettedato == null) SLETTET_SUFFIX else EMPTY_STRING}",
                        overordnetEnhetOrgNr = underEnhet.overordnetEnhet,
                    ),
                ),
            )

            if (underEnhet.overordnetEnhet != null) {
                val moderEnhet = hentEnhet(underEnhet.overordnetEnhet)
                if (moderEnhet != null) {
                    return EnhetMedOverordnetEnhet(
                        organisasjonsnummer = organisasjonsnummer,
                        navn = "${underEnhet.navn}${if (underEnhet.slettedato != null) SLETTET_SUFFIX else EMPTY_STRING}",
                        overordnetEnhetOrganisasjonsnummer = underEnhet.overordnetEnhet,
                        overordnetEnhetNavn = moderEnhet.navn,
                    )
                }
            } else {
                return EnhetMedOverordnetEnhet(
                    organisasjonsnummer = organisasjonsnummer,
                    navn = "${underEnhet.navn}${if (underEnhet.slettedato != null) SLETTET_SUFFIX else EMPTY_STRING}",
                    overordnetEnhetOrganisasjonsnummer = null,
                    overordnetEnhetNavn = null,
                )
            }
        }

        val moderEnhet = bronnoysundClient.hentModerenhet(organisasjonsnummer)
        if (moderEnhet != null) {
            log.info("Fant manglende moderenhet orgnr=$organisasjonsnummer i brreg : $moderEnhet ")

            oppdaterEnheter(
                listOf(
                    UpsertEnhet(
                        organisasjonsnummer = organisasjonsnummer,
                        navn = "${moderEnhet.navn}${if (moderEnhet.slettedato != null) SLETTET_SUFFIX else EMPTY_STRING}",
                        overordnetEnhetOrgNr = null,
                    ),
                ),
            )
            return EnhetMedOverordnetEnhet(
                organisasjonsnummer = organisasjonsnummer,
                navn = moderEnhet.navn,
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null,
            )
        }
        return null
    }

    fun oppdaterEnheter(upserts: List<UpsertEnhet>) {
        upserts.forEach { upsert ->
            transactionTemplate.executeWithoutResult {
                enhetRepository.upsertEnhet(
                    UpsertEnhetCmd(
                        organisasjonsnummer = upsert.organisasjonsnummer,
                        navn = upsert.navn,
                        overordnetEnhet = upsert.overordnetEnhetOrgNr,
                    ),
                )
                kafkaProducerService.publiserVirksomhet(
                    VirksomhetV1Dto(
                        organisasjonsnummer = upsert.organisasjonsnummer,
                        navn = upsert.navn,
                        overordnetEnhetOrganisasjonsnummer = upsert.overordnetEnhetOrgNr,
                    ),
                )
            }
        }
    }

    data class UpsertEnhet(
        val organisasjonsnummer: String,
        val navn: String,
        val overordnetEnhetOrgNr: String?,
    )

    data class EnhetMedOverordnetEnhet(
        val organisasjonsnummer: String,
        val navn: String,
        val overordnetEnhetOrganisasjonsnummer: String? = null,
        val overordnetEnhetNavn: String? = null,
    )

    companion object {
        private const val SLETTET_SUFFIX = " (slettet)"
        private const val EMPTY_STRING = ""
    }
}
