package no.nav.enhetsregister.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.service.registry.ImportHttpServices
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "brreg-api", types = [BronnoysundApi::class])
class BronnoysundClientConfig {
    @Bean
    fun bronnoysundBulkRestClient(
        builder: RestClient.Builder,
        @Value($$"${spring.http.serviceclient.brreg-api.base-url}") baseUrl: String,
        @Value($$"${spring.http.clients.connect-timeout}") connectTimeout: Duration,
        @Value($$"${app.env.brreg-bulk-read-timeout}") bulkReadTimeout: Duration,
    ): RestClient {
        val requestFactory = ClientHttpRequestFactoryBuilder
            .detect()
            .build(
                HttpClientSettings
                    .defaults()
                    .withConnectTimeout(connectTimeout)
                    .withReadTimeout(bulkReadTimeout),
            )

        return builder
            .clone()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
