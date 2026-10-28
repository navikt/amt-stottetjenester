package no.nav.amt.person.service.internal

import no.nav.amt.person.service.api.request.NavBrukerRequest
import no.nav.common.job.JobRunner
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Suppress("SpringMvcPathVariableDeclarationInspection")
@RestController
@RequestMapping("/internal")
class InternalController(
    private val internalService: InternalService,
) {
    @PostMapping("/person/{dollyIdent}")
    fun opprettPerson(
        @PathVariable("dollyIdent") dollyIdent: String,
    ) = internalService.opprettPerson(dollyIdent)

    @PostMapping("/nav-bruker/{dollyIdent}")
    fun opprettNavBruker(
        @PathVariable("dollyIdent") dollyIdent: String,
    ) = internalService.opprettNavBruker(dollyIdent)

    @PostMapping("/person/identer")
    fun oppdaterPersonidenter(
        @RequestParam(value = "offset", required = false) offset: Int?,
    ) {
        JobRunner.runAsync("oppdater_personidenter") {
            internalService.oppdaterPersonidenter(offset ?: 0)
        }
    }

    @GetMapping("/person/navn/{id}")
    fun oppdaterNavn(
        @PathVariable id: UUID,
    ) = internalService.oppdaterNavn(id)

    @GetMapping("/nav-brukere/republiser")
    fun republiserNavBrukere(
        @RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
        @RequestParam(value = "batchSize", required = false) batchSize: Int?,
    ) {
        JobRunner.runAsync("republiser-nav-brukere") {
            internalService.republiserNavBrukere(startFromOffset ?: 0, batchSize ?: 500)
        }
    }

    @GetMapping("/nav-brukere/oppdater-adr-republiser")
    fun oppdaterOgRepubliserNavBrukere(
        @RequestParam(value = "batchSize", required = false) batchSize: Int = 500,
        @RequestParam(value = "modifiedBefore", required = false) modifiedBefore: LocalDateTime = LocalDateTime.now(),
        @RequestParam(value = "lastId", required = false) lastId: UUID? = null,
    ) {
        JobRunner.runAsync("oppdater-adr-republiser-nav-brukere") {
            internalService.oppdaterAdresseOgRepubliserNavBrukere(batchSize, modifiedBefore, lastId)
        }
    }

    @GetMapping("/nav-bruker/oppdater-adr-republiser/{id}")
    fun oppdaterAdresseOgRepubliserNavBruker(
        @PathVariable("id") id: UUID,
    ) = internalService.oppdaterAdresseOgRepubliserNavBruker(id)

    @GetMapping("/nav-brukere/oppdater-innsats-republiser")
    fun oppdaterOppfolgingInnsatsOgRepubliserNavBrukere(
        @RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
        @RequestParam(value = "batchSize", required = false) batchSize: Int = 500,
        @RequestParam(value = "modifiedBefore", required = false) modifiedBefore: LocalDate? = null,
        @RequestParam(value = "lastId", required = false) lastId: UUID? = null,
    ) {
        JobRunner.runAsync("oppdater-innsats-republiser-nav-brukere") {
            internalService.oppdaterInnsatsOgRepubliserNavBrukere(startFromOffset ?: 0, batchSize, modifiedBefore, lastId)
        }
    }

    @GetMapping("/nav-bruker/oppdater-innsats-republiser/{id}")
    fun oppdaterOppfolgingInnsatsOgRepubliserNavBruker(
        @PathVariable("id") id: UUID,
    ) = internalService.oppdaterInnsatsOgRepubliserNavBruker(id)

    @GetMapping("/nav-brukere/republiser/{navBrukerId}")
    fun republiserNavBruker(
        @PathVariable("navBrukerId") navBrukerId: UUID,
    ) = internalService.publiserNavBruker(navBrukerId)

    @GetMapping("/arrangor-ansatte/republiser")
    fun republiserArrangorAnsatte(
        @RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
        @RequestParam(value = "batchSize", required = false) batchSize: Int?,
    ) {
        JobRunner.runAsync("republiser-arrangor-ansatte") {
            internalService.republiserArrangorAnsatte(startFromOffset ?: 0, batchSize ?: 500)
        }
    }

    @GetMapping("/nav-ansatte/republiser")
    fun republiserNavAnsatte() {
        JobRunner.runAsync("republiser-nav-ansatte") {
            internalService.republiserNavAnsatte()
        }
    }

    @GetMapping("/nav-ansatte/oppdater")
    fun oppdaterNavAnsatte() {
        JobRunner.runAsync("oppdater-nav-ansatte") {
            internalService.oppdaterNavAnsatte()
        }
    }

    @GetMapping("/nav-enhet/oppdater")
    fun oppdaterNavEnheter() = internalService.oppdaterNavEnheter()

    @GetMapping("/nav-brukere/synkroniser-krr")
    fun synkroniserKrr(
        @RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
        @RequestParam(value = "batchSize", required = false) batchSize: Int?,
    ) {
        JobRunner.runAsync("synkroniser-krr-nav-brukere") {
            internalService.synkroniserKrr(startFromOffset ?: 0, batchSize ?: 5000)
        }
    }

    @GetMapping("/nav-brukere/oppdater-manglende-kontaktinfo")
    fun oppdaterManglendeKontakinfo(
        @RequestParam(value = "batchSize", required = false) batchSize: Int?,
    ) {
        JobRunner.runAsync("oppdater-manglende-kontaktinfo") {
            internalService.oppdaterManglendeKontaktinfo(batchSize ?: 200)
        }
    }

    @PostMapping("/nav-brukere/synkroniser-krr")
    fun synkroniserKrrForPerson(
        @RequestBody request: NavBrukerRequest,
    ) = internalService.synkroniserKrrForPerson(request.personident)

    @GetMapping("/nav-brukere/republiser-ny-ident")
    fun republiserNavBrukereMedNyIdent() {
        JobRunner.runAsync("republiser-nav-brukere-med-ny-ident") {
            internalService.republiserNavBrukereMedNyIdent()
        }
    }
}
