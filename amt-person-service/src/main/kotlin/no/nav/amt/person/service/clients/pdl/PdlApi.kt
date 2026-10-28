package no.nav.amt.person.service.clients.pdl

import no.nav.amt.person.service.clients.GraphqlRequest
import no.nav.amt.person.service.clients.PDL_API_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import tools.jackson.databind.JsonNode

@ClientRegistrationId(PDL_API_CLIENT_ID)
interface PdlApi {
    @PostExchange("/graphql")
    fun execute(
        @RequestBody query: GraphqlRequest,
    ): ResponseEntity<JsonNode>
}
