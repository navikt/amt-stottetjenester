package no.nav.arrangor.ansatt

import no.nav.arrangor.domain.Ansatt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/service/ansatt")
class AnsattServiceUserAPI(
    private val ansattService: AnsattService,
) {
    @PostMapping
    fun getAnsatt(
        @RequestBody body: AnsattRequestBody,
    ): Ansatt {
        body.validatePersonident()
        return ansattService.get(body.personident)
            ?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): Ansatt = ansattService.get(id)
        ?: throw NoSuchElementException("Ansatt $id eksisterer ikke.")

    @DeleteMapping("/tilganger")
    fun fjernTilgangerHosArrangor(
        @RequestBody request: FjernTilgangerHosArrangorRequest,
    ) {
        ansattService.fjernTilgangerHosArrangor(
            deltakerlisteId = request.deltakerlisteId,
            deltakerIder = request.deltakerIder,
            arrangorId = request.arrangorId,
        )
    }

    data class FjernTilgangerHosArrangorRequest(
        val arrangorId: UUID,
        val deltakerlisteId: UUID,
        val deltakerIder: List<UUID>,
    )

    data class AnsattRequestBody(
        val personident: String,
    ) {
        fun validatePersonident() {
            if (personident.trim().length != 11 || !personident.trim().matches("""\d{11}""".toRegex())) {
                throw IllegalArgumentException("Ugyldig personident")
            }
        }
    }
}
