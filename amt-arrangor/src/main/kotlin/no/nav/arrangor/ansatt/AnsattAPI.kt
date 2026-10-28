package no.nav.arrangor.ansatt

import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.personIdent
import org.slf4j.MDC
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/ansatt")
class AnsattAPI(
    private val ansattService: AnsattService,
) {
    @GetMapping
    fun getByPersonident(
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt {
        val ansatt = ansattService.get(jwt.personIdent())
            ?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
        MDC.put("ansatt-id", ansatt.id.toString())
        return ansatt
    }

    @PostMapping("/koordinator/{arrangorId}/{deltakerlisteId}")
    fun setKoordinatorForDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @PathVariable arrangorId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt = hentPersonidentOgSettMdc(jwt).let { personident ->
        ansattService.setKoordinatorForDeltakerliste(
            personident = personident,
            deltakerlisteId = deltakerlisteId,
            arrangorId = arrangorId,
        )
    }

    @DeleteMapping("/koordinator/{arrangorId}/{deltakerlisteId}")
    fun fjernKoordinatorForDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @PathVariable arrangorId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt = hentPersonidentOgSettMdc(jwt).let { personident ->
        ansattService.fjernKoordinatorForDeltakerliste(
            personident = personident,
            deltakerlisteId = deltakerlisteId,
            arrangorId = arrangorId,
        )
    }

    @PostMapping("/veiledere/{deltakerId}")
    fun oppdaterVeiledereForDeltaker(
        @PathVariable deltakerId: UUID,
        @RequestBody request: OppdaterVeiledereForDeltakerRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        hentPersonidentOgSettMdc(jwt).let { personident ->
            ansattService.oppdaterVeiledereForDeltaker(
                personident = personident,
                deltakerId = deltakerId,
                request = request,
            )
        }
    }

    private fun hentPersonidentOgSettMdc(jwt: Jwt): String = jwt
        .personIdent()
        .also {
            ansattService
                .getAnsattIdForPersonident(it)
                ?.let { id -> MDC.put("ansatt-id", id.toString()) }
        }

    data class OppdaterVeiledereForDeltakerRequest(
        val arrangorId: UUID,
        val veilederSomLeggesTil: List<VeilederAnsatt>,
        val veilederSomFjernes: List<VeilederAnsatt>,
    )

    data class VeilederAnsatt(
        val ansattId: UUID,
        val type: VeilederType,
    )
}
