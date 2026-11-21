package no.nav.amt.arena.acl.integration

import com.ninjasquad.springmockk.MockkBean
import net.javacrumbs.shedlock.core.LockProvider
import no.nav.amt.arena.acl.integration.kafka.KafkaMessageConsumer
import no.nav.amt.arena.acl.integration.utils.MockOAuthServer
import no.nav.amt.arena.acl.mocks.MockArenaOrdsProxyHttpServer
import no.nav.amt.arena.acl.mocks.MockMachineToMachineHttpServer
import no.nav.amt.arena.acl.mocks.MockMulighetsrommetApiServer
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestConstructor
import java.util.concurrent.TimeUnit

@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfiguration::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class IntegrationTestBase : JUnitRepositoryTestBase() {
    @Autowired(required = false)
    lateinit var kafkaMessageConsumer: KafkaMessageConsumer

    @MockkBean(relaxed = true)
    @Suppress("unused")
    private lateinit var mockLockProvider: LockProvider

    @AfterEach
    fun cleanup() {
        mockArenaOrdsProxyHttpServer.reset()
        mockMulighetsrommetApiServer.reset()

        if (::kafkaMessageConsumer.isInitialized) kafkaMessageConsumer.reset()
    }

    init {
        Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS)
    }

    companion object {
        val oAuthServer = MockOAuthServer()
        val mockMachineToMachineHttpServer = MockMachineToMachineHttpServer()
        val mockMulighetsrommetApiServer = MockMulighetsrommetApiServer()
        val mockArenaOrdsProxyHttpServer = MockArenaOrdsProxyHttpServer()

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused")
        fun startEnvironment(registry: DynamicPropertyRegistry) {
            mockMulighetsrommetApiServer.start()
            oAuthServer.start()

            registry.add("no.nav.security.jwt.issuer.azuread.discovery-url", oAuthServer::getDiscoveryUrl)
            registry.add("no.nav.security.jwt.issuer.azuread.accepted-audience") { "test-aud" }
            registry.add("mulighetsrommet-api.url") { mockMulighetsrommetApiServer.serverUrl() }
            registry.add("mulighetsrommet-api.scope") { "test.mulighetsrommet-api" }

            mockArenaOrdsProxyHttpServer.start()
            registry.add("amt-arena-ords-proxy.scope") { "test.amt-arena-ords-proxy" }
            registry.add("amt-arena-ords-proxy.url") { mockArenaOrdsProxyHttpServer.serverUrl() }

            mockMachineToMachineHttpServer.start()
            registry.add("nais.env.azureOpenIdConfigTokenEndpoint") {
                mockMachineToMachineHttpServer.serverUrl() + MockMachineToMachineHttpServer.TOKEN_PATH
            }
        }
    }
}
