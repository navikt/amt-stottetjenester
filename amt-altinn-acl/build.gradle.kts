import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.4.21"

    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "no.nav.amt-altinn-acl"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

val commonVersion = "4.2026.09.24_06.17-80dfc0eacb29"
val amtLibVersion = "1.2026.10.20_09.19-755cf2186842"
val logstashEncoderVersion = "9.0"
val mockkVersion = "1.14.11"
val kotestVersion = "6.2.6"
val springmockkVersion = "5.0.1"
val toolsJacksonVersion = "3.2.4"

dependencies {
    constraints {
        implementation("at.yawk.lz4:lz4-java") {
            version { strictly("1.11.4") }
            because("Fixes CVE-2026-59949")
        }
    }

    // Spring Boot, HTTP-klient og sikkerhet
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    runtimeOnly("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Applikasjonsfunksjoner og serialisering
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin:$toolsJacksonVersion")

    // Database og migreringer
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Nav-biblioteker
    implementation("no.nav.amt.deltakelser.lib:utils:$amtLibVersion")
    implementation("no.nav.amt.deltakelser.lib:spring-boot:$amtLibVersion")
    implementation("no.nav.common:rest:$commonVersion")
    implementation("no.nav.common:job:$commonVersion")

    // Metrikker og strukturert logging
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // Spring Boot-teststøtte
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers-postgresql")

    // Assertions, mocking og coroutines i tester
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version = "1.8.0"
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
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
