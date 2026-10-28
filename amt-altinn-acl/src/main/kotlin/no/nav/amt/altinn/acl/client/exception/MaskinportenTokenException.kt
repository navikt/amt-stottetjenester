package no.nav.amt.altinn.acl.client.exception

class MaskinportenTokenException(
    val statusCode: Int,
    val errorCode: String,
) : RuntimeException("Klarte ikke å hente Maskinporten-token code=$statusCode error=$errorCode")
