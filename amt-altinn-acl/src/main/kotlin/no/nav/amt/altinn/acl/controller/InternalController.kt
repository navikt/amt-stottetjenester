package no.nav.amt.altinn.acl.controller

import no.nav.amt.altinn.acl.jobs.AltinnUpdater
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal")
class InternalController(
    private val altinnUpdater: AltinnUpdater,
) {
    @GetMapping("/altinn/synkroniser")
    suspend fun synkroniserAltinnRettigheter() = altinnUpdater.update()
}
