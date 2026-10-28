package no.nav.amt.person.service.api

import no.nav.amt.person.service.api.dto.AdressebeskyttelseDto
import no.nav.amt.person.service.api.dto.ArrangorAnsattDto
import no.nav.amt.person.service.api.dto.NavAnsattDto
import no.nav.amt.person.service.api.dto.NavBrukerDto
import no.nav.amt.person.service.api.dto.NavBrukerFodselsdatoDto
import no.nav.amt.person.service.api.dto.NavEnhetDto
import no.nav.amt.person.service.api.request.AdressebeskyttelseRequest
import no.nav.amt.person.service.api.request.ArrangorAnsattRequest
import no.nav.amt.person.service.api.request.NavAnsattRequest
import no.nav.amt.person.service.api.request.NavBrukerRequest
import no.nav.amt.person.service.api.request.NavEnhetRequest
import no.nav.amt.person.service.clients.krr.Kontaktinformasjon
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.navansatt.NavAnsattRepository
import no.nav.amt.person.service.navansatt.NavAnsattService
import no.nav.amt.person.service.navbruker.NavBrukerService
import no.nav.amt.person.service.navenhet.NavEnhetRepository
import no.nav.amt.person.service.navenhet.NavEnhetService
import no.nav.amt.person.service.person.ArrangorAnsattService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class PersonApiController(
    private val navAnsattRepository: NavAnsattRepository,
    private val navAnsattService: NavAnsattService,
    private val navBrukerService: NavBrukerService,
    private val navEnhetsRepository: NavEnhetRepository,
    private val navEnhetService: NavEnhetService,
    private val arrangorAnsattService: ArrangorAnsattService,
    private val pdlClient: PdlClient,
) {
    @PostMapping("/nav-bruker")
    fun hentEllerOpprettNavBruker(
        @RequestBody request: NavBrukerRequest,
    ) = NavBrukerDto.fromDbo(navBrukerService.hentEllerOpprettNavBruker(request.personident))

    @PostMapping("/nav-bruker-fodselsar")
    fun hentNavBrukerFodselsar(
        @RequestBody request: NavBrukerRequest,
    ) = NavBrukerFodselsdatoDto(pdlClient.hentPersonFodselsar(request.personident))

    @PostMapping("/nav-bruker/kontaktinformasjon")
    fun hentNavBrukerKontaktinformasjon(
        @RequestBody personidenter: Set<String>,
    ): Map<String, Kontaktinformasjon> = navBrukerService.fetchOppdatertKontaktinfo(personidenter)

    @PostMapping("/nav-ansatt")
    fun hentEllerOpprettNavAnsatt(
        @RequestBody request: NavAnsattRequest,
    ) = NavAnsattDto.fromDbo(navAnsattService.hentEllerOpprettAnsatt(request.navIdent))

    @GetMapping("/nav-ansatt/{id}")
    fun hentNavAnsatt(
        @PathVariable id: UUID,
    ) = NavAnsattDto.fromDbo(navAnsattRepository.get(id))

    @PostMapping("/arrangor-ansatt")
    fun hentEllerOpprettArrangorAnsatt(
        @RequestBody request: ArrangorAnsattRequest,
    ) = ArrangorAnsattDto.fromDbo(arrangorAnsattService.hentEllerOpprettAnsatt(request.personident))

    @PostMapping("/nav-enhet")
    fun hentEllerOpprettNavEnhet(
        @RequestBody request: NavEnhetRequest,
    ): NavEnhetDto = navEnhetService
        .hentEllerOpprettNavEnhet(request.enhetId)
        ?.let { NavEnhetDto.fromDbo(it) }
        ?: throw NoSuchElementException("Klarte ikke å hente Nav-enhet med enhet-id: ${request.enhetId}")

    @GetMapping("/nav-enhet/{id}")
    fun hentNavEnhet(
        @PathVariable id: UUID,
    ) = NavEnhetDto.fromDbo(navEnhetsRepository.get(id))

    @PostMapping("/person/adressebeskyttelse")
    fun hentAdressebeskyttelse(
        @RequestBody request: AdressebeskyttelseRequest,
    ) = AdressebeskyttelseDto(pdlClient.hentAdressebeskyttelse(request.personident))
}
