package no.nav.amt_arena_ords_proxy.client.ords

import no.nav.amt_arena_ords_proxy.type.ArbeidsgiverId
import no.nav.amt_arena_ords_proxy.type.Fnr
import no.nav.amt_arena_ords_proxy.type.PersonId

interface ArenaOrdsClient {
	fun hentFnr(personIdListe: List<PersonId>): List<PersonIdWithFnr>

	fun hentArbeidsgiver(arbeidsgiverId: ArbeidsgiverId): Arbeidsgiver?
}

data class PersonIdWithFnr(
	val personId: PersonId,
	val fnr: Fnr?, // Fnr kan være null i visse tilfeller fordi man i eldre versjoner av arena kunne nulle ut fnr
)

data class Arbeidsgiver(
	val bedriftsnr: Int?,
	val orgnrMorselskap: Int,
)
