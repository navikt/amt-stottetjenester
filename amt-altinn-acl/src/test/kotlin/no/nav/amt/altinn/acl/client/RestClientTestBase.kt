package no.nav.amt.altinn.acl.client

import no.nav.amt.altinn.acl.config.ClientConfig
import org.junit.jupiter.api.AfterEach
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
        "ALTINN_SCOPE=altinn:accessmanagement/authorizedparties.resourceowner",
        "ALTINN3_URL=http://localhost:9999/altinn",
        "spring.http.serviceclient.maskinporten.base-url=http://localhost:9999/maskinporten/token",
        "spring.http.serviceclient.altinn3.base-url=http://localhost:9999/altinn",
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

    @AfterEach
    fun verifyServer() = server.verify()
}
