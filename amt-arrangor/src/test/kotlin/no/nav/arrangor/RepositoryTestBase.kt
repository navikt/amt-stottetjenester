package no.nav.arrangor

import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.database.DbTestDataUtils.cleanDatabase
import no.nav.arrangor.database.TestDatabaseService
import no.nav.arrangor.deltaker.DeltakerRepository
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureJdbc
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

@ActiveProfiles("test")
@SpringBootTest(
    classes = [
        AnsattRepository::class,
        AnsattArrangorRepository::class,
        ArrangorRepository::class,
        DeltakerRepository::class,
        TestDatabaseService::class,
    ],
)
@AutoConfigureJdbc
@AutoConfigureJson
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RepositoryTestBase {
    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    protected lateinit var testDatabase: TestDatabaseService

    @AfterEach
    fun cleanDatabase() = cleanDatabase(dataSource)

    companion object {
        private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"

        @ServiceConnection
        private val postgres = PostgreSQLContainer(
            DockerImageName
                .parse(POSTGRES_DOCKER_IMAGE_NAME)
                .asCompatibleSubstituteFor("postgres"),
        ).apply {
            addEnv("TZ", "Europe/Oslo")
            waitingFor(Wait.forListeningPort())
        }
    }
}
