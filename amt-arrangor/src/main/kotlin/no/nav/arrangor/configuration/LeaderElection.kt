package no.nav.arrangor.configuration

import no.nav.amt.lib.utils.leaderelection.Leader
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import no.nav.amt.lib.utils.leaderelection.LeaderProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody

@Configuration(proxyBeanMethods = false)
class LeaderElection(
    @Value($$"${elector.path}") private val electorPath: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    @Bean
    fun leaderElectionClient(): LeaderElectionClient {
        val leaderProvider = LeaderProvider { path ->
            restClient
                .get()
                .uri(if (path.startsWith("http://")) path else "http://$path")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, response ->
                    throw RuntimeException("Kall mot elector feiler med HTTP-${response.statusCode.value()}")
                }.requiredBody<Leader>()
        }

        return LeaderElectionClient(leaderProvider, electorPath)
    }
}
