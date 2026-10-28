import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.4.21"

    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
}

group = "no.nav.amt_enhetsregister"
version = "0.0.1-SNAPSHOT"
description = "ACL mot Brønnøysundregisteret"

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

val jacksonModuleKotlinVersion = "3.2.4"
val commonVersion = "4.2026.09.24_06.17-80dfc0eacb29"
val amtLibVersion = "1.2026.10.20_09.19-755cf2186842"
val logstashEncoderVersion = "9.0"
val shedlockVersion = "7.10.1"
val mockOauth2ServerVersion = "6.1.3"
val mockkVersion = "1.14.11"
val springMockkVersion = "5.0.1"

dependencies {
    constraints {
        implementation("at.yawk.lz4:lz4-java") {
            version { strictly("1.11.4") }
            because("Fixes CVE-2026-59949")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    runtimeOnly("org.springframework.boot:spring-boot-starter-jetty")
    runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("org.springframework.boot:spring-boot-flyway")

    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")

    implementation("no.nav.common:job:$commonVersion")
    implementation("no.nav.common:rest:$commonVersion")
    implementation("no.nav.common:kafka:$commonVersion") {
        exclude("org.apache.avro", "avro")
        exclude("org.xerial.snappy", "snappy-java")
        exclude("io.confluent", "kafka-avro-serializer")
    }
    implementation("no.nav.amt.deltakelser.lib:spring-boot:$amtLibVersion")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:$shedlockVersion")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xjsr305=strict")
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
