package no.nav.amt.person.service.data

import no.nav.amt.person.service.clients.nom.NomQueries
import no.nav.amt.person.service.clients.pdl.PdlPerson
import no.nav.amt.person.service.navansatt.NavAnsattDbo
import no.nav.amt.person.service.navbruker.Adressebeskyttelse
import no.nav.amt.person.service.navbruker.InnsatsgruppeV1
import no.nav.amt.person.service.navbruker.NavBrukerDbo
import no.nav.amt.person.service.navbruker.Oppfolgingsperiode
import no.nav.amt.person.service.navenhet.NavEnhetDbo
import no.nav.amt.person.service.person.dbo.PersonDbo
import no.nav.amt.person.service.person.dbo.PersonidentDbo
import no.nav.amt.person.service.person.model.Adresse
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.Bostedsadresse
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Kontaktadresse
import no.nav.amt.person.service.person.model.Matrikkeladresse
import no.nav.amt.person.service.person.model.Personident
import no.nav.amt.person.service.person.model.Vegadresse
import no.nav.amt.person.service.poststed.Postnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun randomIdent(): String = (10_00_19_00_00_000..31_12_20_99_99_999).random().toString()

    val navGrunerlokka = NavEnhetDbo(
        id = UUID(0L, 0L),
        navn = "Nav Grünerløkka",
        enhetId = "0315",
    )

    val orgTilknytning = listOf(
        NomQueries.OrgTilknytning(
            gyldigFom = LocalDate.of(2020, 1, 1),
            gyldigTom = null,
            orgEnhet = NomQueries.OrgTilknytning.OrgEnhet("0315"),
            erDagligOppfolging = true,
        ),
    )

    fun randomNavIdent(): String = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

    private val enhetIdCounter = java.util.concurrent.atomic
        .AtomicInteger(1000)

    fun randomEnhetId(): String =
        enhetIdCounter.getAndUpdate { current -> if (current >= 9999) 1000 else current + 1 }.toString().padStart(4, '0')

    fun lagPerson(
        id: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        erFalskIdentitet: Boolean = false,
        fornavn: String = "Fornavn",
        mellomnavn: String? = null,
        etternavn: String = "Etternavn",
        createdAt: LocalDateTime = LocalDateTime.now(),
        modifiedAt: LocalDateTime = LocalDateTime.now(),
    ) = PersonDbo(
        id = id,
        personident = personident,
        erFalskIdentitet = erFalskIdentitet,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )

    fun lagNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetId: String = randomEnhetId(),
        navn: String = "Nav Enhet 1",
        createdAt: LocalDateTime = LocalDateTime.now(),
        modifiedAt: LocalDateTime = LocalDateTime.now(),
    ) = NavEnhetDbo(id, enhetId, navn, createdAt, modifiedAt)

    fun lagNavAnsatt(
        id: UUID = UUID.randomUUID(),
        navIdent: String = randomNavIdent(),
        navn: String = "Nav Ansatt",
        telefon: String = "99988777",
        epost: String = "ansatt@nav.no",
        createdAt: LocalDateTime = LocalDateTime.now(),
        modifiedAt: LocalDateTime = LocalDateTime.now(),
    ) = NavAnsattDbo(
        id = id,
        navIdent = navIdent,
        navn = navn,
        telefon = telefon,
        epost = epost,
        navEnhetId = navGrunerlokka.id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )

    fun lagNavBruker(
        id: UUID = UUID.randomUUID(),
        person: PersonDbo = lagPerson(),
        navVeileder: NavAnsattDbo? = lagNavAnsatt(),
        navEnhet: NavEnhetDbo? = lagNavEnhet(),
        telefon: String? = "77788999",
        epost: String? = "nav_bruker@gmail.com",
        erSkjermet: Boolean = false,
        adresse: Adresse? = null,
        sisteKrrSync: LocalDateTime? = null,
        createdAt: LocalDateTime = LocalDateTime.now(),
        modifiedAt: LocalDateTime = LocalDateTime.now(),
        adressebeskyttelse: Adressebeskyttelse? = null,
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: InnsatsgruppeV1? = InnsatsgruppeV1.STANDARD_INNSATS,
    ) = NavBrukerDbo(
        id = id,
        person = person,
        navVeileder = navVeileder,
        navEnhet = navEnhet,
        telefon = telefon,
        epost = epost,
        erSkjermet = erSkjermet,
        adresse = adresse,
        sisteKrrSync = sisteKrrSync,
        adressebeskyttelse = adressebeskyttelse,
        oppfolgingsperioder = oppfolgingsperioder,
        innsatsgruppe = innsatsgruppe,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )

    fun lagOppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startdato: LocalDateTime = LocalDateTime.now().minusMonths(1),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(
        id,
        startdato,
        sluttdato,
    )

    fun lagPdlPerson(
        person: PersonDbo,
        telefon: String? = null,
        adressebeskyttelseGradering: AdressebeskyttelseGradering? = null,
        identer: List<Personident> = listOf(Personident(person.personident, false, IdentType.FOLKEREGISTERIDENT)),
        adresse: Adresse? = lagAdresse(),
    ) = PdlPerson(
        erFalskIdentitet = person.erFalskIdentitet,
        fornavn = person.fornavn,
        mellomnavn = person.mellomnavn,
        etternavn = person.etternavn,
        telefonnummer = telefon,
        adressebeskyttelseGradering = adressebeskyttelseGradering,
        identer = identer,
        adresse = adresse,
    )

    fun lagPersonident(
        ident: String = randomIdent(),
        personId: UUID = UUID.randomUUID(),
        historisk: Boolean = false,
        type: IdentType = IdentType.FOLKEREGISTERIDENT,
        modifiedAt: LocalDateTime = LocalDateTime.now(),
        createdAt: LocalDateTime = LocalDateTime.now(),
    ) = PersonidentDbo(ident, personId, historisk, type, modifiedAt, createdAt)

    fun lagAdresse(): Adresse = Adresse(
        bostedsadresse =
            Bostedsadresse(
                coAdressenavn = "C/O Gutterommet",
                vegadresse = null,
                matrikkeladresse =
                    Matrikkeladresse(
                        tilleggsnavn = "Gården",
                        postnummer = "0484",
                        poststed = "OSLO",
                    ),
            ),
        oppholdsadresse = null,
        kontaktadresse =
            Kontaktadresse(
                coAdressenavn = null,
                vegadresse =
                    Vegadresse(
                        husnummer = "1",
                        husbokstav = null,
                        adressenavn = "Gate",
                        tilleggsnavn = null,
                        postnummer = "1234",
                        poststed = "MOSS",
                    ),
                postboksadresse = null,
            ),
    )

    val postnumreInTest =
        setOf(
            Postnummer("0484", "OSLO"),
            Postnummer("5341", "STRAUME"),
            Postnummer("5365", "TURØY"),
            Postnummer("5449", "BØMLO"),
            Postnummer("9609", "NORDRE SEILAND"),
        )
}
