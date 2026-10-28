dependencies {
    api(project(":amt-tiltak:db-migrations"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.tiltak.testcontainers)
    api(libs.otj.pg.embedded)
    api(libs.tiltak.testcontainers.postgresql)
    api("com.zaxxer:HikariCP")
    api("org.flywaydb:flyway-core")
    api("org.flywaydb:flyway-database-postgresql")
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:db_utils"))
    api(libs.tiltak.kotest.assertions.core)
}
