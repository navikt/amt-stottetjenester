// Utvider Spring-oppsettet med JDBC, Flyway og PostgreSQL, inkludert databasetester.
plugins {
    id("amt-spring-conventions")
}

dependencies {
    // JDBC og PostgreSQL-driver.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // Databasemigrasjoner med Flyway.
    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // JDBC-testing mot PostgreSQL i Testcontainers.
    testImplementation("org.springframework.boot:spring-boot-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
