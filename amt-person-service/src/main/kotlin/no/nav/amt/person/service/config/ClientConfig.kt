package no.nav.amt.person.service.config

import no.nav.amt.person.service.clients.AO_OPPFOLGINGSKONTOR_CLIENT_ID
import no.nav.amt.person.service.clients.BEHANDLINGSNUMMER_HEADER
import no.nav.amt.person.service.clients.BEHANDLINGSNUMMER_HEADER_VALUE
import no.nav.amt.person.service.clients.DIGDIR_KRR_PROXY_CLIENT_ID
import no.nav.amt.person.service.clients.GEN_TEMA_HEADER_VALUE
import no.nav.amt.person.service.clients.KODEVERK_API_CLIENT_ID
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER
import no.nav.amt.person.service.clients.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.person.service.clients.NOM_API_CLIENT_ID
import no.nav.amt.person.service.clients.NORG_API_CLIENT_ID
import no.nav.amt.person.service.clients.PDL_API_CLIENT_ID
import no.nav.amt.person.service.clients.TEMA_HEADER
import no.nav.amt.person.service.clients.VEILARBOPPFOLGING_CLIENT_ID
import no.nav.amt.person.service.clients.VEILARBVEDTAKSSTOTTE_CLIENT_ID
import no.nav.amt.person.service.clients.kodeverk.KodeverkApi
import no.nav.amt.person.service.clients.krr.KrrProxyApi
import no.nav.amt.person.service.clients.nom.NomApi
import no.nav.amt.person.service.clients.norg.NorgApi
import no.nav.amt.person.service.clients.oppfolgingskontor.OppfolgingskontorApi
import no.nav.amt.person.service.clients.pdl.PdlApi
import no.nav.amt.person.service.clients.veilarboppfolging.VeilarboppfolgingApi
import no.nav.amt.person.service.clients.veilarbvedtaksstotte.VeilarbvedtaksstotteApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
// Det er én HTTP-klient per gruppe, så vi bruker klient-ID som gruppenavn
@ImportHttpServices(group = DIGDIR_KRR_PROXY_CLIENT_ID, types = [KrrProxyApi::class])
@ImportHttpServices(group = VEILARBOPPFOLGING_CLIENT_ID, types = [VeilarboppfolgingApi::class])
@ImportHttpServices(group = VEILARBVEDTAKSSTOTTE_CLIENT_ID, types = [VeilarbvedtaksstotteApi::class])
@ImportHttpServices(group = KODEVERK_API_CLIENT_ID, types = [KodeverkApi::class])
@ImportHttpServices(group = NOM_API_CLIENT_ID, types = [NomApi::class])
@ImportHttpServices(group = AO_OPPFOLGINGSKONTOR_CLIENT_ID, types = [OppfolgingskontorApi::class])
@ImportHttpServices(group = PDL_API_CLIENT_ID, types = [PdlApi::class])
@ImportHttpServices(group = NORG_API_CLIENT_ID, types = [NorgApi::class])
class ClientConfig {
    @Bean
    fun httpServiceGroupConfigurer() = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group, builder ->
            builder
                .defaultHeaders { headers ->
                    headers.accept = listOf(MediaType.APPLICATION_JSON)
                    headers.set(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE)

                    if (group.name() == PDL_API_CLIENT_ID) {
                        headers.set(TEMA_HEADER, GEN_TEMA_HEADER_VALUE)
                        headers.set(BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_HEADER_VALUE)
                    }
                }
        }
    }
}
