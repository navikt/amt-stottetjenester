package no.nav.amt.person.service.clients.nom

import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.amt.person.service.clients.GraphqlRequest
import no.nav.amt.person.service.clients.GraphqlResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class NomClient(
    private val nomApi: NomApi,
    private val objectMapper: ObjectMapper,
) {
    fun hentNavAnsatt(navIdent: String): NomNavAnsatt? = hentNavAnsatte(listOf(navIdent))
        .firstOrNull()
        .also { if (it == null) log.info("Fant ikke veileder i NOM med ident $navIdent") }

    fun hentNavAnsatte(navIdenter: List<String>): List<NomNavAnsatt> {
        val jsonResponse = executeUpstreamCallWithRequiredBody(
            serviceName = "NOM",
            operation = "utfør GraphQL-spørring",
        ) {
            nomApi.execute(
                GraphqlRequest(
                    query = hentRessurserQuery,
                    variables = mapOf(QUERY_IDENTER to navIdenter),
                ),
            )
        }
        val response = GraphqlResponse(jsonResponse, objectMapper)

        val ressurser: List<NomQueries.RessursResult> = response.dataAt(RESSURSER) ?: return emptyList()

        return ressurser.mapNotNull { result ->
            if (result.code != NomQueries.ResultCode.OK || result.ressurs == null) {
                log.warn("Fant ikke veileder i NOM. statusCode=${result.code}")
                return@mapNotNull null
            }
            val ansatt = result.ressurs
            NomNavAnsatt(
                navIdent = ansatt.navident,
                navn = ansatt.visningsnavn ?: "${ansatt.fornavn} ${ansatt.etternavn}",
                epost = ansatt.epost,
                telefonnummer = hentTjenesteTelefonnummer(ansatt),
                orgTilknytning = ansatt.orgTilknytning,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NomClient::class.java)
        private const val QUERY_IDENTER = "identer"
        private const val RESSURSER = "ressurser"
        private const val NAV_KONTOR_TELEFON = "NAV_KONTOR_TELEFON"
        private const val NAV_TJENESTE_TELEFON = "NAV_TJENESTE_TELEFON"

        private val hentRessurserQuery = GraphqlResponse.loadDocument("hentRessurser")

        private fun hentTjenesteTelefonnummer(ansatt: NomQueries.Ressurs): String? =
            ansatt.telefon.find { it.type == NAV_KONTOR_TELEFON }?.nummer
                ?: ansatt.telefon.find { it.type == NAV_TJENESTE_TELEFON }?.nummer
                ?: ansatt.primaryTelefon
    }
}
