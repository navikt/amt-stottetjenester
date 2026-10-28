package no.nav.amt.person.service.clients.nom

import no.nav.amt.person.service.clients.GraphqlRequest
import no.nav.amt.person.service.clients.NOM_API_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import tools.jackson.databind.JsonNode

@ClientRegistrationId(NOM_API_CLIENT_ID)
interface NomApi {
    @PostExchange("/graphql")
    fun execute(
        @RequestBody query: GraphqlRequest,
    ): ResponseEntity<JsonNode>
}
