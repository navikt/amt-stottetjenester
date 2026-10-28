package no.nav.arrangor.configuration

import no.nav.arrangor.client.AMT_ALTINN_CLIENT_ID
import no.nav.arrangor.client.AMT_ENHETSREGISTER_CLIENT_ID
import no.nav.arrangor.client.AMT_PERSON_CLIENT_ID
import no.nav.arrangor.client.altinn.AltinnAclApi
import no.nav.arrangor.client.enhetsregister.EnhetsregisterApi
import no.nav.arrangor.client.person.PersonApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = AMT_ALTINN_CLIENT_ID, types = [AltinnAclApi::class])
@ImportHttpServices(group = AMT_ENHETSREGISTER_CLIENT_ID, types = [EnhetsregisterApi::class])
@ImportHttpServices(group = AMT_PERSON_CLIENT_ID, types = [PersonApi::class])
class ClientConfig {
    @Bean
    fun httpServiceGroupConfigurer() = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { _, builder ->
            builder.defaultHeaders { headers ->
                headers.accept = listOf(MediaType.APPLICATION_JSON)
            }
            builder.defaultStatusHandler({ status -> status.value() == 404 }) { request, _ ->
                throw NoSuchElementException("[${request.method}] ${request.uri}: 404")
            }
        }
    }
}
