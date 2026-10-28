package no.nav.amt.altinn.acl.client.altinn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.altinn.acl.client.exception.AltinnClientException
import no.nav.amt.altinn.acl.client.exception.MaskinportenTokenException
import no.nav.amt.altinn.acl.domain.RolleType
import org.junit.jupiter.api.Test

class Altinn3ClientUnitTest {
    private val altinnApi = mockk<AltinnApi>()
    private val sut = Altinn3Client(altinnApi)

    @Test
    fun `hentRoller - Maskinporten-feil - kaster Altinn-feil med feildetaljer`() {
        every { altinnApi.hentAuthorizedParties(any()) } throws
            MaskinportenTokenException(
                statusCode = 401,
                errorCode = "invalid_client",
            )

        val exception = shouldThrow<AltinnClientException> {
            sut.hentRoller("12345678901", RolleType.entries)
        }

        exception.statusCode shouldBe 401
        exception.errorCode shouldBe "invalid_client"
        exception.cause shouldBe null
    }
}
