package no.nav.arrangor.client.person

import no.nav.amt.kontrakter.person.ArrangorAnsattDto
import no.nav.amt.kontrakter.person.ArrangorAnsattRequest
import no.nav.arrangor.client.AMT_PERSON_CLIENT_ID
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

@ClientRegistrationId(AMT_PERSON_CLIENT_ID)
interface PersonApi {
    @PostExchange("/api/arrangor-ansatt")
    fun hentPersonalia(
        @RequestBody request: ArrangorAnsattRequest,
    ): ArrangorAnsattDto
}
