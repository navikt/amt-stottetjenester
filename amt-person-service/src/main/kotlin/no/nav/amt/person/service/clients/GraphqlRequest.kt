package no.nav.amt.person.service.clients

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.treeToValue

data class GraphqlRequest(
    val query: String,
    val variables: Any,
)

/**
 * Resultat fra en GraphQL-spørring med typed data-parsing og feilhåndtering.
 */
class GraphqlResponse(
    private val response: JsonNode,
    val objectMapper: ObjectMapper,
    val serviceName: String = "GraphQL",
) {
    val data: JsonNode?
        get() = response["data"]?.takeUnless { it.isNull }

    val errors: JsonNode?
        get() = response["errors"]?.takeIf { !it.isNull && it.isArray && !it.isEmpty }

    val extensions: JsonNode?
        get() = response["extensions"]?.takeUnless { it.isNull }

    fun throwOnErrors() {
        val errors = errors ?: return
        val melding = buildString {
            append("Feilmeldinger i respons fra $serviceName:\n")
            if (data == null) append("- data i respons er null \n")
            errors.forEach { error ->
                append("- ${error["message"]?.asString()}\n")
            }
        }
        throw RuntimeException(melding)
    }

    inline fun <reified T> dataAt(field: String): T? {
        val node = data?.get(field)?.takeUnless { it.isNull } ?: return null
        return objectMapper.treeToValue<T>(node)
    }

    fun requiredData(): JsonNode = data ?: throw RuntimeException("$serviceName respons inneholder ikke data")

    inline fun <reified T> requiredDataAt(field: String): T {
        val node = requiredData()[field]?.takeUnless { it.isNull }
            ?: throw RuntimeException("$serviceName respons inneholder ikke data")
        return objectMapper.treeToValue<T>(node)
    }

    companion object {
        fun loadDocument(name: String): String = ClassPathResource("graphql-documents/$name.graphql").getContentAsString(Charsets.UTF_8)
    }
}
