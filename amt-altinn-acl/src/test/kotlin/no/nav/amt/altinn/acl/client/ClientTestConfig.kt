package no.nav.amt.altinn.acl.client

import io.mockk.every
import io.mockk.mockk
import no.nav.amt.altinn.acl.client.maskinporten.MaskinportenTokenClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer

/**
 * Testkonfigurasjon for deklarative HTTP-klienter (jf. amt-arrangor sitt RestClientTestBase-mønster).
 *
 * Binder hver http-klient-gruppe (definert via @ImportHttpServices) til sin egen MockRestServiceServer,
 * og erstatter den ekte Maskinporten-klienten med en deterministisk mock.
 */
@TestConfiguration(proxyBeanMethods = false)
class ClientTestConfig {
    private val mocks = mutableMapOf<String, MockRestServiceServer>()

    @Bean
    fun mockServerConfigurer(environment: Environment) = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group, builder ->
            val baseUrl = environment.getRequiredProperty("spring.http.serviceclient.${group.name()}.base-url")
            builder.baseUrl(baseUrl)
            mocks[group.name()] = MockRestServiceServer.bindTo(builder).build()
        }
    }

    @Bean
    @Primary
    fun maskinportenTokenClient(): MaskinportenTokenClient = mockk {
        every { getAccessToken() } returns "altinn3-token"
    }

    fun getMock(group: String): MockRestServiceServer = mocks[group] ?: error("No mock for group '$group'")
}
