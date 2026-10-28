package no.nav.amt.person.service.integration

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import no.nav.amt.person.service.clients.kodeverk.KodeverkClient
import no.nav.amt.person.service.clients.krr.KrrProxyClient
import no.nav.amt.person.service.clients.nom.NomClient
import no.nav.amt.person.service.clients.norg.NorgClient
import no.nav.amt.person.service.clients.oppfolgingskontor.OppfolgingskontorClient
import no.nav.amt.person.service.clients.pdl.PdlClient
import no.nav.amt.person.service.clients.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.person.service.clients.veilarbvedtaksstotte.VeilarbvedtaksstotteClient
import no.nav.amt.person.service.data.RepositoryTestBase
import no.nav.poao_tilgang.client.PoaoTilgangClient
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper

@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTestBase : RepositoryTestBase() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var krrProxyClient: KrrProxyClient

    @MockkBean
    lateinit var nomClient: NomClient

    @MockkBean
    lateinit var norgClient: NorgClient

    @MockkBean
    lateinit var oppfolgingskontorClient: OppfolgingskontorClient

    @MockkBean
    lateinit var pdlClient: PdlClient

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var poaoTilgangClient: PoaoTilgangClient

    @MockkBean
    lateinit var veilarboppfolgingClient: VeilarboppfolgingClient

    @MockkBean
    lateinit var veilarbvedtaksstotteClient: VeilarbvedtaksstotteClient

    @AfterEach
    fun cleanUp() {
        clearMocks(
            krrProxyClient,
            nomClient,
            norgClient,
            oppfolgingskontorClient,
            pdlClient,
            kodeverkClient,
            poaoTilgangClient,
            veilarboppfolgingClient,
            veilarbvedtaksstotteClient,
        )
    }

    protected fun outboxRecords(): List<OutboxRecord> = template.jdbcTemplate.query(
        "SELECT topic, key, value FROM kafka_producer_record ORDER BY id",
    ) { rs, _ ->
        OutboxRecord(
            topic = rs.getString("topic"),
            key = rs.getBytes("key")?.toString(Charsets.UTF_8),
            value = rs.getBytes("value")?.toString(Charsets.UTF_8),
        )
    }

    protected data class OutboxRecord(
        val topic: String,
        val key: String?,
        val value: String?,
    )
}
