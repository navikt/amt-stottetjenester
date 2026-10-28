package no.nav.amt_arena_ords_proxy.config

import no.nav.amt_arena_ords_proxy.client.ords.Arbeidsgiver
import no.nav.amt_arena_ords_proxy.client.ords.ArenaOrdsClient
import no.nav.amt_arena_ords_proxy.client.ords.PersonIdWithFnr
import no.nav.amt_arena_ords_proxy.type.ArbeidsgiverId
import no.nav.amt_arena_ords_proxy.type.PersonId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LocalApplicationConfig {
	@Bean
	fun arenaOrdsClient(): ArenaOrdsClient =
		object : ArenaOrdsClient {
			override fun hentFnr(personIdListe: List<PersonId>): List<PersonIdWithFnr> {
				TODO("Not yet implemented")
			}

			override fun hentArbeidsgiver(arbeidsgiverId: ArbeidsgiverId): Arbeidsgiver {
				TODO("Not yet implemented")
			}
		}
}
