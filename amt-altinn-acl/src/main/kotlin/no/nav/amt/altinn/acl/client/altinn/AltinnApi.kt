package no.nav.amt.altinn.acl.client.altinn

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

interface AltinnApi {
    @PostExchange(
        "/accessmanagement/api/v1/resourceowner/authorizedparties",
        contentType = MediaType.APPLICATION_JSON_VALUE,
        accept = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun hentAuthorizedParties(
        @RequestBody body: AuthorizedPartiesRequest,
    ): ResponseEntity<List<AuthorizedParty>>
}
