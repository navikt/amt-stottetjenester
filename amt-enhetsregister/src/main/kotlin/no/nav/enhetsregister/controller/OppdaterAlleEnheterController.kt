package no.nav.enhetsregister.controller

import no.nav.common.job.JobRunner
import no.nav.enhetsregister.service.OppdaterAlleEnheterService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/local/api/oppdater-alle")
class OppdaterAlleEnheterController(
    private val oppdaterAlleEnheterService: OppdaterAlleEnheterService,
) {
    @PostMapping("/moderenheter")
    fun republiseringModerenheter() {
        JobRunner.runAsync("oppdater_alle_moderenheter") {
            oppdaterAlleEnheterService.oppdaterAlleModerenheter()
        }
    }

    @PostMapping("/underenheter")
    fun republiseringUnderenheter() {
        JobRunner.runAsync("oppdater_alle_underenheter") {
            oppdaterAlleEnheterService.oppdaterAlleUnderenheter()
        }
    }
}
