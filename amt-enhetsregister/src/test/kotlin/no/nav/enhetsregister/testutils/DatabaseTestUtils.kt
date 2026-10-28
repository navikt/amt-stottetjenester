package no.nav.enhetsregister.testutils

import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

object DatabaseTestUtils {
    private const val SCHEMA = "public"
    private const val FLYWAY_SCHEMA_HISTORY_TABLE_NAME = "flyway_schema_history"

    fun runScript(
        dataSource: DataSource,
        script: String,
    ) {
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.update(script)
    }

    fun runScriptFile(
        dataSource: DataSource,
        scriptFilePath: String,
    ) {
        val script = ClassPathResource(scriptFilePath).file.readText()
        runScript(dataSource, script)
    }

    fun cleanDatabase(dataSource: DataSource) {
        val jdbcTemplate = JdbcTemplate(dataSource)
        val tables = getAllTables(jdbcTemplate).filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }

        val sequences = getAllSequences(jdbcTemplate)

        tables.forEach {
            jdbcTemplate.update("TRUNCATE TABLE $it CASCADE")
        }

        sequences.forEach {
            jdbcTemplate.update("ALTER SEQUENCE $it RESTART WITH 1")
        }
    }

    fun cleanAndInitDatabase(
        dataSource: DataSource,
        scriptFilePath: String,
    ) {
        cleanDatabase(dataSource)
        runScriptFile(dataSource, scriptFilePath)
    }

    private fun getAllTables(jdbcTemplate: JdbcTemplate): List<String> {
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, SCHEMA)
    }

    private fun getAllSequences(jdbcTemplate: JdbcTemplate): List<String> {
        val sql = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, SCHEMA)
    }
}
