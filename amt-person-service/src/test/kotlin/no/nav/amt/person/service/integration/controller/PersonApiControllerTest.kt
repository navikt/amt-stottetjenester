package no.nav.amt.person.service.integration.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.amt.person.service.TestJwtConfig
import no.nav.amt.person.service.api.dto.AdressebeskyttelseDto
import no.nav.amt.person.service.api.dto.ArrangorAnsattDto
import no.nav.amt.person.service.api.dto.NavAnsattDto
import no.nav.amt.person.service.api.dto.NavBrukerDto
import no.nav.amt.person.service.api.dto.NavBrukerFodselsdatoDto
import no.nav.amt.person.service.api.dto.NavEnhetDto
import no.nav.amt.person.service.clients.krr.Kontaktinformasjon
import no.nav.amt.person.service.clients.nom.NomNavAnsatt
import no.nav.amt.person.service.clients.norg.toNorgNavEnhetDto
import no.nav.amt.person.service.clients.oppfolgingskontor.Arbeidsoppfolging
import no.nav.amt.person.service.data.TestData
import no.nav.amt.person.service.integration.IntegrationTestBase
import no.nav.amt.person.service.navansatt.NavAnsattRepository
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navbruker.NavBrukerRepository
import no.nav.amt.person.service.navenhet.NavEnhetRepository
import no.nav.amt.person.service.person.PersonRepository
import no.nav.amt.person.service.person.PersonidentRepository
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.IdentType
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
@Import(TestJwtConfig::class)
class PersonApiControllerTest(
    private val jwtEncoder: JwtEncoder,
    private val mockMvc: MockMvc,
    private val personidentRepository: PersonidentRepository,
    private val personRepository: PersonRepository,
    private val navBrukerRepository: NavBrukerRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
) : IntegrationTestBase() {
    @Nested
    inner class Autentisering {
        @Test
        fun `skal avvise request uten token`() {
            mockMvc
                .post("/api/nav-bruker") {
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `skal avvise request med token med feil audience`() {
            mockMvc
                .post("/api/nav-bruker") {
                    headers {
                        setBearerAuth(getAzureAdToken(audience = "wrong-audience"))
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = "{}"
                }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `skal avvise azure ad token som ikke er M2M`() {
            mockMvc
                .post("/api/nav-bruker") {
                    headers {
                        setBearerAuth(issueAzureAdToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = "{}"
                }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `skal avvise alle endepunkter uten token`() {
            val postEndpoints = listOf(
                "/api/arrangor-ansatt",
                "/api/nav-ansatt",
                "/api/nav-bruker",
                "/api/nav-bruker-fodselsar",
                "/api/nav-bruker/kontaktinformasjon",
                "/api/nav-enhet",
            )
            val getEndpoints = listOf(
                "/api/nav-ansatt/${UUID.randomUUID()}",
                "/api/nav-enhet/${UUID.randomUUID()}",
            )

            postEndpoints.forEach { endpoint ->
                mockMvc
                    .post(endpoint) {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andExpect { status { isUnauthorized() } }
            }

            getEndpoints.forEach { endpoint ->
                mockMvc
                    .get(endpoint)
                    .andExpect { status { isUnauthorized() } }
            }
        }
    }

    @Nested
    inner class ArrangorAnsatt {
        @Test
        fun `ansatt finnes ikke - skal opprette og returnere`() {
            val person = TestData.lagPerson()
            every { pdlClient.hentPerson(person.personident) } returns TestData.lagPdlPerson(person)

            val result = mockMvc
                .post("/api/arrangor-ansatt") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "${person.personident}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<ArrangorAnsattDto>(result.response.contentAsByteArray)
            val faktiskPerson = personRepository.get(person.personident)

            assertSoftly(faktiskPerson.shouldNotBeNull()) {
                id shouldBe body.id
                personident shouldBe body.personident
                fornavn shouldBe body.fornavn
                mellomnavn shouldBe body.mellomnavn
                etternavn shouldBe body.etternavn
            }
        }

        @Test
        fun `ansatt er navBruker - skal returnere person`() {
            val person = TestData.lagPerson()
            val navBruker = TestData.lagNavBruker(person = person)
            testDataRepository.insertNavBruker(navBruker)

            val result = mockMvc
                .post("/api/arrangor-ansatt") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "${person.personident}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<ArrangorAnsattDto>(result.response.contentAsByteArray)
            val faktiskPerson = personRepository.get(person.personident)

            assertSoftly(faktiskPerson.shouldNotBeNull()) {
                id shouldBe body.id
                personident shouldBe body.personident
                fornavn shouldBe body.fornavn
                mellomnavn shouldBe body.mellomnavn
                etternavn shouldBe body.etternavn
            }
        }
    }

    @Nested
    inner class NavBruker {
        @Test
        fun `bruker finnes ikke - skal opprette og returnere`() {
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val navBruker = TestData.lagNavBruker(navVeileder = navAnsatt, navEnhet = navEnhet)

            mockNavBrukerAvhengigheter(navBruker)

            val result = mockMvc
                .post("/api/nav-bruker") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "${navBruker.person.personident}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavBrukerDto>(result.response.contentAsByteArray)
            val faktiskBruker = navBrukerRepository.get(navBruker.person.personident)

            sammenlignBrukerDtoer(faktiskBruker.shouldNotBeNull(), body)

            val ident = personidentRepository.getAllForPerson(faktiskBruker.person.id).first()

            assertSoftly(ident) {
                it.ident shouldBe body.personident
                type shouldBe IdentType.FOLKEREGISTERIDENT
                historisk shouldBe false
            }
        }

        @Test
        fun `bruker finnes - skal returnere eksisterende`() {
            val navBruker = TestData.lagNavBruker(person = TestData.lagPerson(erFalskIdentitet = true))
            testDataRepository.insertNavBruker(navBruker)

            val result = mockMvc
                .post("/api/nav-bruker") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "${navBruker.person.personident}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val navBrukerDto = objectMapper.readValue<NavBrukerDto>(result.response.contentAsByteArray)
            val faktiskBruker = navBrukerRepository.get(navBruker.person.personident)

            sammenlignBrukerDtoer(faktiskBruker.shouldNotBeNull(), navBrukerDto)
        }

        @Test
        fun `bruker er adressebeskyttet - skal returnere med beskyttelse`() {
            val navAnsatt = TestData.lagNavAnsatt()
            val navEnhet = TestData.lagNavEnhet()
            val navBruker = TestData.lagNavBruker(navVeileder = navAnsatt, navEnhet = navEnhet)

            every { pdlClient.hentPerson(navBruker.person.personident) } returns TestData.lagPdlPerson(
                person = navBruker.person,
                adressebeskyttelseGradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            )
            every { pdlClient.hentTelefon(navBruker.person.personident) } returns navBruker.telefon
            every { veilarboppfolgingClient.hentVeilederIdent(navBruker.person.personident) } returns navAnsatt.navIdent
            every { veilarboppfolgingClient.hentOppfolgingperioder(navBruker.person.personident) } returns
                navBruker.oppfolgingsperioder
            every { veilarbvedtaksstotteClient.hentInnsatsgruppe(navBruker.person.personident) } returns
                InnsatsgruppeV1.SPESIELT_TILPASSET_INNSATS
            every { oppfolgingskontorClient.hentKontorForBruker(navBruker.person.personident) } returns
                Arbeidsoppfolging(kontorId = navEnhet.enhetId, kontorNavn = navEnhet.navn)
            every { krrProxyClient.hentKontaktinformasjon(navBruker.person.personident) } returns
                Result.success(Kontaktinformasjon(epost = navBruker.epost, telefonnummer = navBruker.telefon))
            every { poaoTilgangClient.erSkjermetPerson(navBruker.person.personident) } returns
                ApiResult(result = false, throwable = null)
            every { nomClient.hentNavAnsatt(navAnsatt.navIdent) } returns NomNavAnsatt(
                navIdent = navAnsatt.navIdent,
                navn = navAnsatt.navn,
                telefonnummer = navAnsatt.telefon,
                epost = navAnsatt.epost,
                orgTilknytning = TestData.orgTilknytning,
            )
            every { norgClient.hentNavEnhet(navEnhet.enhetId) } returns navEnhet.toNorgNavEnhetDto()
            every { norgClient.hentNavEnhet(TestData.navGrunerlokka.enhetId) } returns
                TestData.navGrunerlokka.toNorgNavEnhetDto()

            val result = mockMvc
                .post("/api/nav-bruker") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "${navBruker.person.personident}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val navBrukerDto = objectMapper.readValue<NavBrukerDto>(result.response.contentAsByteArray)
            val faktiskBruker = navBrukerRepository.get(navBruker.person.personident)

            sammenlignBrukerDtoer(faktiskBruker.shouldNotBeNull(), navBrukerDto)
        }
    }

    @Nested
    inner class NavBrukerFodselsar {
        @Test
        fun `skal returnere fodselsar fra PDL`() {
            val personident = TestData.randomIdent()
            val forventetFodselsar = 1990

            every { pdlClient.hentPersonFodselsar(personident) } returns forventetFodselsar

            val result = mockMvc
                .post("/api/nav-bruker-fodselsar") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "$personident"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavBrukerFodselsdatoDto>(result.response.contentAsByteArray)
            body.fodselsar shouldBe forventetFodselsar
        }
    }

    @Nested
    inner class NavBrukerKontaktinformasjon {
        @Test
        fun `skal returnere kontaktinformasjon for personidenter`() {
            val navBruker = TestData.lagNavBruker()
            testDataRepository.insertNavBruker(navBruker)

            every { krrProxyClient.hentKontaktinformasjon(setOf(navBruker.person.personident)) } returns
                Result.success(
                    mapOf(
                        navBruker.person.personident to Kontaktinformasjon(
                            epost = navBruker.epost,
                            telefonnummer = navBruker.telefon,
                        ),
                    ),
                )
            every { pdlClient.hentTelefon(navBruker.person.personident) } returns navBruker.telefon

            val result = mockMvc
                .post("/api/nav-bruker/kontaktinformasjon") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = objectMapper.writeValueAsString(setOf(navBruker.person.personident))
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<Map<String, Kontaktinformasjon>>(result.response.contentAsByteArray)
            body[navBruker.person.personident].shouldNotBeNull()
        }
    }

    @Nested
    inner class NavAnsatt {
        @Test
        fun `ansatt er ikke lagret - skal opprette og returnere`() {
            val navAnsatt = TestData.lagNavAnsatt()

            every { nomClient.hentNavAnsatt(navAnsatt.navIdent) } returns NomNavAnsatt(
                navIdent = navAnsatt.navIdent,
                navn = navAnsatt.navn,
                telefonnummer = navAnsatt.telefon,
                epost = navAnsatt.epost,
                orgTilknytning = TestData.orgTilknytning,
            )
            every { norgClient.hentNavEnhet(TestData.navGrunerlokka.enhetId) } returns
                TestData.navGrunerlokka.toNorgNavEnhetDto()

            val result = mockMvc
                .post("/api/nav-ansatt") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"navIdent": "${navAnsatt.navIdent}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavAnsattDto>(result.response.contentAsByteArray)
            val faktiskNavAnsatt = navAnsattRepository.get(navAnsatt.navIdent)

            assertSoftly(faktiskNavAnsatt.shouldNotBeNull()) {
                id shouldBe body.id
                navIdent shouldBe body.navIdent
                navn shouldBe body.navn
                telefon shouldBe body.telefon
                epost shouldBe body.epost
            }
        }

        @Test
        fun `ansatt finnes - skal returnere eksisterende`() {
            val navAnsatt = TestData.lagNavAnsatt()
            testDataRepository.insertNavAnsatt(navAnsatt)

            val result = mockMvc
                .get("/api/nav-ansatt/${navAnsatt.id}") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                    }
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavAnsattDto>(result.response.contentAsByteArray)

            assertSoftly(body) {
                id shouldBe navAnsatt.id
                navIdent shouldBe navAnsatt.navIdent
                navn shouldBe navAnsatt.navn
                telefon shouldBe navAnsatt.telefon
                epost shouldBe navAnsatt.epost
            }
        }
    }

    @Nested
    inner class NavEnhet {
        @Test
        fun `enhet finnes ikke - skal opprette og returnere`() {
            val navEnhet = TestData.lagNavEnhet()

            every { norgClient.hentNavEnhet(navEnhet.enhetId) } returns navEnhet.toNorgNavEnhetDto()

            val result = mockMvc
                .post("/api/nav-enhet") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"enhetId": "${navEnhet.enhetId}"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavEnhetDto>(result.response.contentAsByteArray)
            val faktiskNavEnhet = navEnhetRepository.get(navEnhet.enhetId)

            assertSoftly(faktiskNavEnhet.shouldNotBeNull()) {
                id shouldBe body.id
                enhetId shouldBe body.enhetId
                navn shouldBe body.navn
            }
        }

        @Test
        fun `enhet finnes - skal returnere eksisterende`() {
            val navEnhet = TestData.lagNavEnhet()
            testDataRepository.insertNavEnhet(navEnhet)

            val result = mockMvc
                .get("/api/nav-enhet/${navEnhet.id}") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                    }
                }.andExpect { status { isOk() } }
                .andReturn()

            val body = objectMapper.readValue<NavEnhetDto>(result.response.contentAsByteArray)

            assertSoftly(body) {
                id shouldBe navEnhet.id
                enhetId shouldBe navEnhet.enhetId
                navn shouldBe navEnhet.navn
            }
        }
    }

    @Nested
    inner class Adressebeskyttelse {
        @Test
        fun `person er beskyttet - skal returnere gradering`() {
            val personident = TestData.randomIdent()
            val gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG

            every { pdlClient.hentAdressebeskyttelse(personident) } returns gradering

            val result = mockMvc
                .post("/api/person/adressebeskyttelse") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "$personident"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            objectMapper.readValue<AdressebeskyttelseDto>(result.response.contentAsByteArray).gradering shouldBe gradering
        }

        @Test
        fun `person er ikke beskyttet - skal returnere null gradering`() {
            val personident = TestData.randomIdent()

            every { pdlClient.hentAdressebeskyttelse(personident) } returns null

            val result = mockMvc
                .post("/api/person/adressebeskyttelse") {
                    headers {
                        setBearerAuth(issueAzureAdM2MToken())
                        contentType = MediaType.APPLICATION_JSON
                    }
                    content = """{"personident": "$personident"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

            objectMapper.readValue<AdressebeskyttelseDto>(result.response.contentAsByteArray).gradering shouldBe null
        }
    }

    private fun mockNavBrukerAvhengigheter(navBruker: NavBrukerDbo) {
        every { pdlClient.hentPerson(navBruker.person.personident) } returns TestData.lagPdlPerson(navBruker.person)
        every { pdlClient.hentTelefon(navBruker.person.personident) } returns navBruker.telefon

        every { veilarboppfolgingClient.hentVeilederIdent(navBruker.person.personident) } returns
            (navBruker.navVeileder?.navIdent)

        every { veilarboppfolgingClient.hentOppfolgingperioder(navBruker.person.personident) } returns
            navBruker.oppfolgingsperioder

        every { veilarbvedtaksstotteClient.hentInnsatsgruppe(navBruker.person.personident) } returns
            navBruker.innsatsgruppe

        every { oppfolgingskontorClient.hentKontorForBruker(navBruker.person.personident) } returns
            navBruker.navEnhet?.let { Arbeidsoppfolging(kontorId = it.enhetId, kontorNavn = it.navn) }

        every { krrProxyClient.hentKontaktinformasjon(navBruker.person.personident) } returns
            Result.success(Kontaktinformasjon(epost = navBruker.epost, telefonnummer = navBruker.telefon))

        every { poaoTilgangClient.erSkjermetPerson(navBruker.person.personident) } returns
            ApiResult(result = navBruker.erSkjermet, throwable = null)

        if (navBruker.navVeileder != null) {
            every { nomClient.hentNavAnsatt(navBruker.navVeileder.navIdent) } returns NomNavAnsatt(
                navIdent = navBruker.navVeileder.navIdent,
                navn = navBruker.navVeileder.navn,
                telefonnummer = navBruker.navVeileder.telefon,
                epost = navBruker.navVeileder.epost,
                orgTilknytning = TestData.orgTilknytning,
            )
        }

        if (navBruker.navEnhet != null) {
            every { norgClient.hentNavEnhet(navBruker.navEnhet.enhetId) } returns
                navBruker.navEnhet.toNorgNavEnhetDto()
        }

        // NavAnsattService needs norg to resolve the ansatt's enhet
        every { norgClient.hentNavEnhet(TestData.navGrunerlokka.enhetId) } returns
            TestData.navGrunerlokka.toNorgNavEnhetDto()
    }

    private fun sammenlignBrukerDtoer(
        faktiskBruker: NavBrukerDbo,
        brukerDto: NavBrukerDto,
    ) {
        assertSoftly(faktiskBruker) {
            assertSoftly(person) {
                id shouldBe brukerDto.personId
                personident shouldBe brukerDto.personident
                fornavn shouldBe brukerDto.fornavn
                mellomnavn shouldBe brukerDto.mellomnavn
                etternavn shouldBe brukerDto.etternavn
                erFalskIdentitet shouldBe brukerDto.harFalskIdentitet
            }

            telefon shouldBe brukerDto.telefon
            epost shouldBe brukerDto.epost
            navVeileder?.id shouldBe brukerDto.navVeilederId
            navEnhet?.id shouldBe brukerDto.navEnhet?.id
            navEnhet?.enhetId shouldBe brukerDto.navEnhet?.enhetId
            navEnhet?.navn shouldBe brukerDto.navEnhet?.navn
            erSkjermet shouldBe brukerDto.erSkjermet
            adressebeskyttelse shouldBe brukerDto.adressebeskyttelse
            oppfolgingsperioder shouldBe brukerDto.oppfolgingsperioder
            innsatsgruppe shouldBe brukerDto.innsatsgruppe
        }
    }

    private fun issueAzureAdM2MToken(sub: UUID = UUID.randomUUID()): String = getAzureAdToken(
        sub = sub,
        roles = listOf("access_as_application"),
    )

    private fun issueAzureAdToken(): String = getAzureAdToken(
        sub = UUID.randomUUID(),
        roles = emptyList(),
    )

    private fun getAzureAdToken(
        sub: UUID = UUID.randomUUID(),
        oid: UUID = UUID.randomUUID(),
        audience: String = "amt-person-service-client-id",
        roles: List<String> = emptyList(),
    ): String {
        val claims = JwtClaimsSet
            .builder()
            .issuer("http://localhost:9999/azuread")
            .subject(sub.toString())
            .audience(listOf(audience))
            .claim("oid", oid.toString())
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
