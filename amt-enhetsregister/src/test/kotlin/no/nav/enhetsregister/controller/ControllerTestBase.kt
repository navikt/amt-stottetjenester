package no.nav.enhetsregister.controller

import no.nav.enhetsregister.config.SecurityConfig
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@ActiveProfiles("test")
@Import(SecurityConfig::class)
abstract class ControllerTestBase {
    protected fun bearerToken(
        roles: List<String> = listOf("access_as_application"),
        audience: String = "test-aud",
        issuerId: String = "azuread",
    ): String = requireNotNull(oauthServer)
        .issueToken(
            issuerId = issuerId,
            subject = "test",
            audience = audience,
            claims = mapOf("roles" to roles),
        ).serialize()

    companion object {
        private var oauthServer: MockOAuth2Server? = null

        @Suppress("unused")
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val server = MockOAuth2Server().apply { start() }
            oauthServer = server

            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                server.wellKnownUrl("azuread").toString().removeSuffix("/.well-known/openid-configuration")
            }
            registry.add("spring.security.oauth2.resourceserver.jwt.audiences") { "test-aud" }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            oauthServer?.shutdown()
            oauthServer = null
        }
    }
}
