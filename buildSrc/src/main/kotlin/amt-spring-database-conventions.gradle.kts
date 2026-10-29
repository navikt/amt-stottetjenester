import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("amt-spring-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(versionCatalog.findLibrary("mockk").get())
    testImplementation(versionCatalog.findLibrary("springmockk").get())
}
