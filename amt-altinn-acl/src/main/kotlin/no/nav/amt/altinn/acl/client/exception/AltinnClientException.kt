package no.nav.amt.altinn.acl.client.exception

class AltinnClientException(
    val statusCode: Int,
    val errorCode: String? = null,
) : RuntimeException("Klarte ikke å hente organisasjoner fra Altinn, status=$statusCode")
