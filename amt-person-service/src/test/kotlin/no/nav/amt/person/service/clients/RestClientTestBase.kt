package no.nav.amt.person.service.clients

import no.nav.amt.person.service.config.ClientConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer

@Import(ClientTestConfig::class, ClientConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
    properties = [
        "spring.http.serviceclient.pdl-api.base-url=http://pdl-api",
        "spring.http.serviceclient.digdir-krr-proxy.base-url=http://digdir-krr-proxy",
        "spring.http.serviceclient.veilarboppfolging.base-url=http://veilarboppfolging/veilarboppfolging",
        "spring.http.serviceclient.veilarbvedtaksstotte.base-url=http://veilarbvedtaksstotte/veilarbvedtaksstotte",
        "spring.http.serviceclient.kodeverk-api.base-url=http://kodeverk-api",
        "spring.http.serviceclient.nom-api.base-url=http://nom-api",
        "spring.http.serviceclient.ao-oppfolgingskontor.base-url=http://ao-oppfolgingskontor",
        "spring.http.serviceclient.norg-api.base-url=http://norg",
        "spring.test.restclient.mockrestserviceserver.enabled=false",
    ],
)
abstract class RestClientTestBase(
    private val group: String,
) {
    @Autowired
    private lateinit var testConfig: ClientTestConfig

    lateinit var server: MockRestServiceServer

    @BeforeEach
    fun resetServer() {
        server = testConfig.getMock(group)
        server.reset()
    }
}
