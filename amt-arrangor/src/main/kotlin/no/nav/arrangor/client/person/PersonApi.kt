package no.nav.arrangor.client.person

import no.nav.arrangor.client.AMT_PERSON_CLIENT_ID
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import java.util.UUID

@ClientRegistrationId(AMT_PERSON_CLIENT_ID)
interface PersonApi {
    @PostExchange("/api/arrangor-ansatt")
    fun hentPersonalia(
        @RequestBody request: PersonRequest,
    ): PersonResponse

    data class PersonRequest(
        val personident: String,
    )

    data class PersonResponse(
        val id: UUID,
        val personident: String,
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )
}
