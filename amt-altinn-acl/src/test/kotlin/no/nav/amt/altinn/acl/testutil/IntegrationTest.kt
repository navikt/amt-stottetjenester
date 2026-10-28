package no.nav.amt.altinn.acl.testutil

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import no.nav.amt.altinn.acl.client.altinn.Altinn3Client
import no.nav.amt.altinn.acl.config.SecurityConfig
import no.nav.amt.altinn.acl.domain.RolleType
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant
import java.util.UUID

@Import(TestJwtConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest : RepositoryTestBase() {
    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    /**
     * Altinn-klienten mockes her i stedet for å kjøre en lokal HTTP-mockserver.
     * HTTP-kontrakten mot Altinn dekkes av AltinnApiTest (MockRestServiceServer) og
     * tokenhentingen av MaskinportenTokenClientTest, så integrasjonstestene
     * kan konsentrere seg om applikasjonens egen oppførsel.
     */
    @MockkBean
    lateinit var altinnClient: Altinn3Client

    @AfterEach
    fun clearClientMocks() = clearMocks(altinnClient)

    /**
     * Speiler responsen fra Altinn3Client.hentRoller: alle forespurte rolletyper er med i mappet,
     * men kun de rollene personen faktisk har peker på organisasjonsnumre.
     */
    protected fun mockAltinnRoller(
        norskIdent: String,
        roller: List<RolleType>,
        organisasjonsnummer: List<String>,
    ) {
        every { altinnClient.hentRoller(norskIdent, RolleType.entries) } returns
            RolleType.entries.associateWith { rolleType ->
                if (rolleType in roller) organisasjonsnummer else emptyList()
            }
    }

    protected fun issueAzureAdM2MToken(): String = issueAzureAdToken(
        roles = listOf(SecurityConfig.ACCESS_AS_APPLICATION_ROLE),
    )

    protected fun issueAzureAdToken(
        subject: UUID = UUID.randomUUID(),
        audience: String = TEST_AUDIENCE,
        roles: List<String> = emptyList(),
    ): String {
        val claims = JwtClaimsSet
            .builder()
            .issuer(TEST_ISSUER)
            .subject(subject.toString())
            .audience(listOf(audience))
            .claim("oid", UUID.randomUUID().toString())
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    companion object {
        // Må stemme med src/test/resources/application-test.yml
        private const val TEST_ISSUER = "http://localhost:9999/azuread"
        private const val TEST_AUDIENCE = "test-aud"
    }
}
