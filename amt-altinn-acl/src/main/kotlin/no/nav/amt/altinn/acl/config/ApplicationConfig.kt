package no.nav.amt.altinn.acl.config

import no.nav.amt.lib.utils.leaderelection.Leader
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import no.nav.amt.lib.utils.leaderelection.LeaderProvider
import no.nav.common.rest.filter.LogRequestFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ApplicationConfig(
    @Value($$"${elector.path}") private val electorPath: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    @Bean
    fun leaderElectionClient(): LeaderElectionClient {
        val leaderProvider = LeaderProvider { path ->
            val url = if (path.startsWith("http://")) path else "http://$path"

            restClient
                .get()
                .uri(url)
                .retrieve()
                .requiredBody<Leader>()
        }

        return LeaderElectionClient(leaderProvider, electorPath)
    }

    @Bean
    fun logFilterRegistrationBean(): FilterRegistrationBean<LogRequestFilter> = FilterRegistrationBean<LogRequestFilter>().apply {
        setFilter(LogRequestFilter("amt-altinn-acl", false))
        order = 1
        addUrlPatterns("/*")
    }
}
