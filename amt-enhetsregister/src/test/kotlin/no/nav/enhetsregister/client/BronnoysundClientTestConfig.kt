package no.nav.enhetsregister.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer

@TestConfiguration(proxyBeanMethods = false)
class BronnoysundClientTestConfig {
    private val servers = mutableMapOf<String, MockRestServiceServer>()

    @Bean
    fun bronnoysundHttpServiceGroupConfigurer(environment: Environment) = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group, builder ->
            val baseUrl = environment.getRequiredProperty(
                "spring.http.serviceclient.${group.name()}.base-url",
            )
            builder.baseUrl(baseUrl)
            servers[group.name()] = MockRestServiceServer.bindTo(builder).build()
        }
    }

    fun getMock(group: String): MockRestServiceServer = servers[group]
        ?: error("No mock server for group '$group'")
}
