package no.nav.amt.altinn.acl.service

import no.nav.amt.altinn.acl.client.exception.AltinnClientException

internal fun Exception.safeStatusCode(): Int? = (this as? AltinnClientException)?.statusCode

internal fun Exception.safeErrorCode(): String? = (this as? AltinnClientException)?.errorCode
