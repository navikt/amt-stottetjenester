package no.nav.amt.person.service.clients.oppfolgingskontor

import no.nav.amt.person.service.clients.AO_OPPFOLGINGSKONTOR_CLIENT_ID
import no.nav.amt.person.service.clients.GraphqlRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import tools.jackson.databind.JsonNode

@ClientRegistrationId(AO_OPPFOLGINGSKONTOR_CLIENT_ID)
interface OppfolgingskontorApi {
    @PostExchange("/graphql")
    fun execute(
        @RequestBody request: GraphqlRequest,
    ): ResponseEntity<JsonNode>
}
