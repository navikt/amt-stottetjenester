package no.nav.amt.altinn.acl.client.maskinporten

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

interface MaskinportenTokenApi {
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun hentAccessToken(
        @RequestBody body: MultiValueMap<String, String>,
    ): ResponseEntity<MaskinportenTokenResponse>

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class MaskinportenTokenResponse(
        val accessToken: String,
    )
}
