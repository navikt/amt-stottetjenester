package no.nav.amt.altinn.acl.controller

import no.nav.amt.altinn.acl.domain.RolleType
import no.nav.amt.altinn.acl.service.RolleService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rolle")
class RolleController(
    private val rolleService: RolleService,
) {
    @PostMapping("/tiltaksarrangor")
    fun hentTiltaksarrangorRoller(
        @RequestBody hentRollerRequest: HentRollerRequest,
    ): HentRollerResponse {
        val personident = hentRollerRequest.validatedPersonident()

        val tiltaksarrangorRoller = rolleService
            .getRollerForPerson(personident)
            .map { rolle ->
                HentRollerResponse.TiltaksarrangorRoller(
                    rolle.organisasjonsnummer,
                    rolle.roller.map { it.rolleType },
                )
            }

        return HentRollerResponse(tiltaksarrangorRoller)
    }

    data class HentRollerRequest(
        val personident: String,
    ) {
        fun validatedPersonident(): String {
            val normalizedPersonident = personident.trim()

            if (normalizedPersonident.length != 11 || !normalizedPersonident.matches("""\d{11}""".toRegex())) {
                throw IllegalArgumentException("Ugyldig personident")
            }

            return normalizedPersonident
        }
    }

    data class HentRollerResponse(
        val roller: List<TiltaksarrangorRoller>,
    ) {
        data class TiltaksarrangorRoller(
            val organisasjonsnummer: String,
            val roller: List<RolleType>,
        )
    }
}
