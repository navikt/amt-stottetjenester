package no.nav.arrangor

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.client.person.PersonApi
import no.nav.arrangor.client.person.PersonClient
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest : RepositoryTestBase() {
    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @MockkBean
    protected lateinit var altinnAclClient: AltinnAclClient

    @MockkBean
    protected lateinit var enhetsregisterClient: EnhetsregisterClient

    @MockkBean
    protected lateinit var personClient: PersonClient

    @AfterEach
    fun resetClientMocksAfterTest() {
        resetClientMocks()
    }

    companion object {
        const val TOKEN_X = "tokenx"
        const val AZURE_AD = "azuread"

        private val mockOAuth2Server = MockOAuth2Server()

        private fun getDiscoveryUrl(issuer: String = TOKEN_X): String = mockOAuth2Server.wellKnownUrl(issuer).toString()

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused")
        fun registerProperties(registry: DynamicPropertyRegistry) {
            mockOAuth2Server.start()
            registry.add(
                "AZURE_OPENID_CONFIG_ISSUER",
            ) { getDiscoveryUrl(AZURE_AD).removeSuffix("/.well-known/openid-configuration") }
            registry.add("TOKEN_X_ISSUER") { getDiscoveryUrl(TOKEN_X).removeSuffix("/.well-known/openid-configuration") }
        }
    }

    protected fun mockVirksomhet(virksomhet: Virksomhet) {
        every { enhetsregisterClient.hentVirksomhet(virksomhet.organisasjonsnummer) } returns Result.success(virksomhet)
    }

    protected fun mockAltinnRoller(
        personident: String,
        roller: Map<String, List<no.nav.arrangor.domain.AnsattRolle>>,
    ) {
        mockAltinnRoller(personident, roller.map { (organisasjonsnummer, roller) -> AltinnRolle(organisasjonsnummer, roller) })
    }

    protected fun mockAltinnRoller(
        personident: String,
        roller: List<AltinnRolle>,
    ) {
        every { altinnAclClient.hentRoller(personident) } returns Result.success(roller)
    }

    protected fun mockPerson(
        personident: String,
        id: UUID,
        fornavn: String,
        mellomnavn: String?,
        etternavn: String,
    ) {
        every { personClient.hentPersonalia(personident) } returns Result.success(
            PersonApi.PersonResponse(id, personident, fornavn, mellomnavn, etternavn),
        )
    }

    protected fun resetClientMocks() {
        clearMocks(altinnAclClient, enhetsregisterClient, personClient)
    }

    protected fun getTokenxToken(
        fnr: String,
        audience: String = "amt-arrangor-client-id",
        issuerId: String = TOKEN_X,
        clientId: String = "amt-tiltaksarrangor-bff",
        expiry: Long = 3600,
        claims: Map<String, Any> =
            mapOf(
                "acr" to "Level4",
                "idp" to "idporten",
                "client_id" to clientId,
                "pid" to fnr,
            ),
    ): String = mockOAuth2Server
        .issueToken(
            issuerId,
            clientId,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = UUID.randomUUID().toString(),
                audience = listOf(audience),
                claims = claims,
                expiry = expiry,
            ),
        ).serialize()

    protected fun getAzureAdToken(
        subject: String = "test",
        audience: String = "test-aud",
        issuerId: String = AZURE_AD,
        expiry: Long = 3600,
        claims: Map<String, Any> = mapOf("roles" to listOf("access_as_application")),
    ): String = mockOAuth2Server
        .issueToken(
            issuerId,
            subject,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = subject,
                audience = listOf(audience),
                claims = claims,
                expiry = expiry,
            ),
        ).serialize()
}

class TestResponse(
    private val response: MockHttpServletResponse,
) {
    val code: Int
        get() = response.status

    val contentType: String?
        get() = response.contentType

    fun header(name: String): String? = response.getHeader(name)

    val body = TestResponseBody(response.contentAsByteArray)
}

class TestResponseBody(
    private val content: ByteArray,
) {
    fun string(): String = content.decodeToString()
}
