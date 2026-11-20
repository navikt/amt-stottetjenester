package no.nav.enhetsregister.kafka

import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import no.nav.enhetsregister.repository.EnhetRepository
import no.nav.enhetsregister.repository.RepositoryTestBase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource

@SpringBootTest(classes = [EnhetRepository::class])
class KafkaOutboxMigrationTest : RepositoryTestBase() {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `outbox migration supports fresh database and upgrade from V03`(upgrade: Boolean) {
        val schema = "outbox_migration_test"
        val template = JdbcTemplate(dataSource)
        try {
            if (upgrade) {
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate()
                template.update("INSERT INTO $schema.enhet (organisasjonsnummer, navn) VALUES ('123456789', 'Original')")
            }
            val flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load()
            val result = flyway.migrate()
            flyway.validate()
            assertThat(result.migrationsExecuted).isEqualTo(if (upgrade) 1 else 5)

            dataSource.connection.use { connection ->
                connection.schema = schema
                try {
                    val repository = PostgresJdbcTemplateProducerRepository(JdbcTemplate(SingleConnectionDataSource(connection, true)))
                    val id = repository.storeRecord(StoredProducerRecord("test-topic", "key".toByteArray(), "value".toByteArray(), "[]"))
                    assertThat(repository.getRecords(100).single().id).isEqualTo(id)
                } finally {
                    connection.schema = "public"
                }
            }
            if (upgrade) {
                assertThat(template.queryForObject("SELECT navn FROM $schema.enhet WHERE organisasjonsnummer = '123456789'", String::class.java))
                    .isEqualTo("Original")
            }
        } finally {
            template.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
        }
    }
}
