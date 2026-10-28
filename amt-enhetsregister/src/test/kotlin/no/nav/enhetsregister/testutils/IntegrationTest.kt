package no.nav.enhetsregister.testutils

import no.nav.enhetsregister.repository.RepositoryTestBase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(KafkaTestConfiguration::class)
abstract class IntegrationTest : RepositoryTestBase() {
    companion object {
        val oAuthServer = MockOAuthServer()

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused")
        fun registerProperties(registry: DynamicPropertyRegistry) {
            oAuthServer.start()

            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", oAuthServer::getIssuerUrl)
            registry.add("spring.security.oauth2.resourceserver.jwt.audiences") { "test-aud" }
        }
    }
}
