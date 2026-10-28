package no.nav.arrangor.client

import no.nav.arrangor.configuration.ClientConfig
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
        "spring.http.serviceclient.amt-enhetsregister.base-url=http://amt-enhetsregister",
        "spring.http.serviceclient.amt-altinn.base-url=http://amt-altinn",
        "spring.http.serviceclient.amt-person.base-url=http://amt-person",
        "AMT_ENHETSREGISTER_ALLOWED_HOSTS=amt-enhetsregister",
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
