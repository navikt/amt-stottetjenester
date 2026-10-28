import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    constraints {
        implementation("at.yawk.lz4:lz4-java") {
            version { strictly("1.11.4") }
            because("Fixes CVE-2026-59949")
        }
    }

    // Spring Boot – kjerne og webserver (Jetty i stedet for medfølgende Tomcat)
    runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")

    // Database – JDBC, Flyway-migrering og PostgreSQL-driver
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")

    // Kafka
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation(libs.kafka.clients) {
        exclude("org.xerial.snappy", "snappy-java")
    }

    // Utgående HTTP-kall og OAuth2 (TokenX/Azure AD client credentials)
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Serialisering og Kotlin-coroutines
    implementation(libs.tools.jackson.module.kotlin)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Observability – metrikker og strukturert logging
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(libs.logstash.encoder)

    // Nav-interne biblioteker
    implementation(libs.nav.common.log)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.job)
    implementation(libs.nav.common.kafka)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)
    implementation(libs.amt.lib.utils)
    implementation(libs.amt.lib.spring.boot)

    // Test – Spring Boot testoppsett (JDBC, RestClient, MVC)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    // Test – Testcontainers (Postgres og Kafka)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    // Test – autentisering, assertions og mocking
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version = libs.versions.ktlint.cli.version.get()
}

configurations.named("ktlint") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            requested.version?.let {
                useVersion(it)
                because("ktlint must use the Kotlin versions it was compiled against")
            }
        }
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
