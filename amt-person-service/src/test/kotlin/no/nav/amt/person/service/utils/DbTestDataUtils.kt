package no.nav.amt.person.service.utils

import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

object DbTestDataUtils {
    private const val SCHEMA = "public"

    const val FLYWAY_SCHEMA_HISTORY_TABLE_NAME = "flyway_schema_history"

    fun cleanDatabase(dataSource: DataSource) {
        val jdbcTemplate = JdbcTemplate(dataSource)

        getAllTables(jdbcTemplate).filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }.forEach {
            @Suppress("SqlSourceToSinkFlow")
            jdbcTemplate.update("TRUNCATE TABLE $it CASCADE")
        }

        getAllSequences(jdbcTemplate).forEach {
            @Suppress("SqlSourceToSinkFlow")
            jdbcTemplate.update("ALTER SEQUENCE $it RESTART WITH 1")
        }
    }

    private fun getAllTables(jdbcTemplate: JdbcTemplate): List<String> = jdbcTemplate.query(
        "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
        { rs, _ -> rs.getString(1) },
        SCHEMA,
    )

    private fun getAllSequences(jdbcTemplate: JdbcTemplate): List<String> = jdbcTemplate.query(
        "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?",
        { rs, _ -> rs.getString(1) },
        SCHEMA,
    )
}
