package no.nav.amt.person.service.clients.oppfolgingskontor

import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.amt.person.service.clients.GraphqlRequest
import no.nav.amt.person.service.clients.GraphqlResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class OppfolgingskontorClient(
    private val api: OppfolgingskontorApi,
    private val objectMapper: ObjectMapper,
) {
    fun hentKontorForBruker(ident: String): Arbeidsoppfolging? {
        val jsonResponse = executeUpstreamCallWithRequiredBody(
            serviceName = "ao-oppfolgingskontor",
            operation = "utfør GraphQL-spørring",
        ) {
            api.execute(
                GraphqlRequest(
                    query = kontorForBrukerQuery,
                    variables = mapOf(QUERY_IDENT to ident),
                ),
            )
        }
        val response = GraphqlResponse(jsonResponse, objectMapper, SERVICE_NAME)
        response.throwOnErrors()

        val kontorTilhorigheter: KontorTilhorigheter = response.requiredDataAt(KONTOR_TILHORIGHETER)

        return kontorTilhorigheter.arbeidsoppfolging
    }

    companion object {
        private const val QUERY_IDENT = "ident"
        private const val SERVICE_NAME = "ao-oppfolgingskontor"
        private const val KONTOR_TILHORIGHETER = "kontorTilhorigheter"

        private val kontorForBrukerQuery = GraphqlResponse.loadDocument("hentKontorForBruker")
    }

    private data class KontorTilhorigheter(
        val arbeidsoppfolging: Arbeidsoppfolging? = null,
    )
}
