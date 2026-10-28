package no.nav.amt.altinn.acl.client.altinn

data class AuthorizedPartiesRequest(
    val value: String,
    val type: String = "urn:altinn:person:identifier-no",
)
