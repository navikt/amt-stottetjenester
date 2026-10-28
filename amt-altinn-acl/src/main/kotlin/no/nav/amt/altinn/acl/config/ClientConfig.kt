package no.nav.amt.altinn.acl.config

import no.nav.amt.altinn.acl.client.altinn.AltinnApi
import no.nav.amt.altinn.acl.client.maskinporten.MaskinportenTokenApi
import no.nav.amt.altinn.acl.client.maskinporten.MaskinportenTokenClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.CollectionUtils
import org.springframework.util.MultiValueMap
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

const val MASKINPORTEN_CLIENT_ID = "maskinporten"
const val ALTINN3_CLIENT_ID = "altinn3"

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = MASKINPORTEN_CLIENT_ID, types = [MaskinportenTokenApi::class])
@ImportHttpServices(group = ALTINN3_CLIENT_ID, types = [AltinnApi::class])
class ClientConfig {
    @Bean
    fun maskinportenTokenRequest(
        @Value($$"${ALTINN_SCOPE}") altinnScope: String,
        @Value($$"${ALTINN3_URL}") altinn3Url: String,
    ): MultiValueMap<String, String> = CollectionUtils.unmodifiableMultiValueMap(
        CollectionUtils.toMultiValueMap(
            mapOf(
                "identity_provider" to listOf("maskinporten"),
                "target" to listOf(altinnScope),
                "resource" to listOf(altinn3Url),
            ),
        ),
    )

    /**
     * Setter bearer-token på forespørsler til Altinn 3.
     *
     * Tokenklienten slås opp først når en forespørsel sendes. Den avhenger av Maskinporten-API-et,
     * som opprettes med denne konfigurasjonen; et umiddelbart oppslag ville gitt en sirkulær
     * avhengighet under oppstart.
     */
    @Bean
    fun httpServiceGroupConfigurer(maskinportenTokenClient: ObjectProvider<MaskinportenTokenClient>) =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.forEachClient { group, builder ->
                if (group.name() == ALTINN3_CLIENT_ID) {
                    builder.requestInterceptor { request, body, execution ->
                        request.headers.setBearerAuth(maskinportenTokenClient.getObject().getAccessToken())
                        execution.execute(request, body)
                    }
                }
            }
        }
}
