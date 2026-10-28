package no.nav.enhetsregister.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@TestConfiguration(proxyBeanMethods = false)
class BronnoysundBulkDownloaderTestConfig {
    @Bean
    fun bronnoysundRestClient(builder: RestClient.Builder): RestClient = builder.baseUrl("http://brreg-api").build()
}
