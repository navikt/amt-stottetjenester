package no.nav.amt.person.service.clients.pdl

import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.amt.person.service.clients.GraphqlRequest
import no.nav.amt.person.service.clients.GraphqlResponse
import no.nav.amt.person.service.person.model.AdressebeskyttelseGradering
import no.nav.amt.person.service.person.model.IdentType
import no.nav.amt.person.service.person.model.Personident
import no.nav.amt.person.service.poststed.PoststedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class PdlClient(
    private val pdlApi: PdlApi,
    private val poststedRepository: PoststedRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentPerson(personident: String): PdlPerson {
        val response = executeQuery(hentPersonQuery, personident)

        val hentPerson: PdlQueries.HentPersonResult = response.requiredDataAt(HENT_PERSON)
        val hentIdenter: PdlQueries.HentIdenterResult = response.requiredDataAt(HENT_IDENTER)

        return toPdlBruker(hentPerson, hentIdenter) { postnummer -> poststedRepository.getPoststeder(postnummer) }
    }

    fun hentPersonFodselsar(personident: String): Int {
        val response = executeQuery(hentPersonFodselsarQuery, personident)

        val hentPerson: PdlQueries.HentPersonFoedselsdatoResult = response.requiredDataAt(HENT_PERSON)

        return hentPerson.foedselsdato.firstOrNull()?.foedselsaar
            ?: throw RuntimeException("PDL person mangler fodselsdato")
    }

    fun hentIdenter(personident: String): List<Personident> {
        val response = executeQuery(hentIdenterQuery, personident)

        val hentIdenter: PdlQueries.HentIdenterResult = response.requiredDataAt(HENT_IDENTER)

        return hentIdenter.identer.map {
            Personident(
                ident = it.ident,
                historisk = it.historisk,
                type = IdentType.valueOf(it.gruppe),
            )
        }
    }

    fun hentTelefon(personident: String): String? {
        val response = executeQuery(hentTelefonQuery, personident)

        val hentPerson: HentTelefonResult = response.requiredDataAt(HENT_PERSON)

        return hentPerson.telefonnummer.toTelefonnummer()
    }

    fun hentAdressebeskyttelse(personident: String): AdressebeskyttelseGradering? {
        val response = executeQuery(hentAdressebeskyttelseQuery, personident)

        val hentPerson: HentAdressebeskyttelseResult = response.requiredDataAt(HENT_PERSON)

        return hentPerson.adressebeskyttelse.toDiskresjonskode()
    }

    private fun executeQuery(
        query: String,
        personident: String,
    ): GraphqlResponse {
        val jsonResponse = executeUpstreamCallWithRequiredBody(
            serviceName = "PDL",
            operation = "utfør GraphQL-spørring",
        ) {
            pdlApi.execute(
                GraphqlRequest(
                    query = query,
                    variables = PdlQueries.Variables(personident),
                ),
            )
        }
        val response = GraphqlResponse(jsonResponse, objectMapper, SERVICE_NAME)
        handlePdlErrors(response)
        logPdlWarnings(response)
        return response
    }

    private fun handlePdlErrors(response: GraphqlResponse) {
        val errors = response.errors ?: return

        val melding = buildString {
            append("Feilmeldinger i respons fra pdl:\n")
            if (response.data == null) append("- data i respons er null \n")
            errors.forEach { error ->
                val extensions = error[EXTENSIONS]
                val code = extensions?.get(CODE)?.asString()
                val detailsNode = extensions?.get(DETAILS)
                val details = detailsNode?.takeIf { !it.isNull }?.let {
                    PdlQueries.PdlErrorDetails(
                        type = it[TYPE]?.asString(),
                        cause = it[CAUSE]?.asString(),
                        policy = it[POLICY]?.asString(),
                    )
                }
                append("- ${error[MESSAGE]?.asString()} (code: $code details: $details)\n")
            }
        }
        throw RuntimeException(melding)
    }

    private fun logPdlWarnings(response: GraphqlResponse) {
        val warnings = response.extensions?.get(WARNINGS)?.takeIf { !it.isNull && it.isArray && !it.isEmpty } ?: return
        val melding = buildString {
            append("Respons fra Pdl inneholder warnings:\n")
            warnings.forEach { warning ->
                append(
                    "query: ${warning[QUERY]?.asString()},\n" +
                        "id: ${warning[ID]?.asString()},\n" +
                        "message: ${warning[MESSAGE]?.asString()},\n" +
                        "details: ${warning[DETAILS]?.asString()}\n",
                )
            }
        }
        log.warn(melding)
    }

    private data class HentTelefonResult(
        val telefonnummer: List<PdlQueries.Attribute.Telefonnummer>,
    )

    private data class HentAdressebeskyttelseResult(
        val adressebeskyttelse: List<PdlQueries.Attribute.Adressebeskyttelse>,
    )

    companion object {
        private const val SERVICE_NAME = "PDL"
        private const val HENT_PERSON = "hentPerson"
        private const val HENT_IDENTER = "hentIdenter"
        private const val EXTENSIONS = "extensions"
        private const val CODE = "code"
        private const val DETAILS = "details"
        private const val TYPE = "type"
        private const val CAUSE = "cause"
        private const val POLICY = "policy"
        private const val MESSAGE = "message"
        private const val WARNINGS = "warnings"
        private const val QUERY = "query"
        private const val ID = "id"

        private val hentPersonQuery = GraphqlResponse.loadDocument("hentPerson")
        private val hentPersonFodselsarQuery = GraphqlResponse.loadDocument("hentPersonFodselsar")
        private val hentIdenterQuery = GraphqlResponse.loadDocument("hentIdenter")
        private val hentTelefonQuery = GraphqlResponse.loadDocument("hentTelefon")
        private val hentAdressebeskyttelseQuery = GraphqlResponse.loadDocument("hentAdressebeskyttelse")
    }
}
