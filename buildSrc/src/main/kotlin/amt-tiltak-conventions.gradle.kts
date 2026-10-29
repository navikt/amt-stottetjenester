import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("io.spring.dependency-management")
}

group = "no.nav.amt-tiltak"
version = "1.0-SNAPSHOT"

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(name: String) = versionCatalog.findVersion(name).get().requiredVersion

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

extra["kotlin.stdlib.default.dependency"] = "false"

configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${catalogVersion("spring-boot")}")
    }
}

configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

configure<KotlinJvmProjectExtension> {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    add("api", versionCatalog.findLibrary("tiltak-lz4-java").get())
    add("api", "org.jetbrains.kotlin:kotlin-stdlib:${catalogVersion("kotlin")}")
    add("api", "org.jetbrains.kotlinx:kotlinx-coroutines-core")
    add("api", "org.jetbrains.kotlin:kotlin-reflect:${catalogVersion("kotlin")}")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(catalogVersion("kotlin"))
            because("Keep Kotlin dependencies aligned with the compiler version")
        }
    }
    resolutionStrategy.force(
        "at.yawk.lz4:lz4-java:${catalogVersion("tiltak-lz4-java")}",
        "com.fasterxml.jackson.core:jackson-annotations:${catalogVersion("jackson2-annotations")}",
        "com.nimbusds:oauth2-oidc-sdk:${catalogVersion("nimbus")}",
        "com.squareup.okhttp3:okhttp:${catalogVersion("okhttp")}",
        "com.squareup.okhttp3:okhttp-jvm:${catalogVersion("okhttp")}",
        "io.getunleash:unleash-client-java:${catalogVersion("tiltak-unleash")}",
        "io.kotest:kotest-assertions-core-jvm:${catalogVersion("tiltak-kotest")}",
        "io.kotest:kotest-assertions-json-jvm:${catalogVersion("tiltak-kotest")}",
        "io.kotest:kotest-framework-concurrency-jvm:${catalogVersion("tiltak-kotest")}",
        "io.kotest:kotest-runner-junit5-jvm:${catalogVersion("tiltak-kotest")}",
        "io.mockk:mockk-jvm:${catalogVersion("mockk")}",
        "net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${catalogVersion("shedlock")}",
        "net.javacrumbs.shedlock:shedlock-spring:${catalogVersion("shedlock")}",
        "net.logstash.logback:logstash-logback-encoder:${catalogVersion("logstash-encoder")}",
        "no.nav.amt.lib:models:${catalogVersion("tiltak-amt-lib")}",
        "no.nav.common:audit-log:${catalogVersion("nav-common")}",
        "no.nav.common:job:${catalogVersion("nav-common")}",
        "no.nav.common:kafka:${catalogVersion("nav-common")}",
        "no.nav.common:log:${catalogVersion("nav-common")}",
        "no.nav.common:rest:${catalogVersion("nav-common")}",
        "no.nav.common:token-client:${catalogVersion("nav-common")}",
        "no.nav.common:types:${catalogVersion("nav-common")}",
        "no.nav.poao-tilgang:client:${catalogVersion("tiltak-poao-tilgang")}",
        "no.nav.security:mock-oauth2-server:${catalogVersion("mock-oauth2-server")}",
        "no.nav.security:token-validation-spring:${catalogVersion("token-support")}",
        "org.apache.kafka:kafka-clients:${catalogVersion("tiltak-kafka-clients")}",
        "org.apache.logging.log4j:log4j-api:${catalogVersion("log4j")}",
        "org.mockito.kotlin:mockito-kotlin:${catalogVersion("mockito-kotlin")}",
        "org.postgresql:postgresql:${catalogVersion("postgresql")}",
        "org.testcontainers:junit-jupiter:${catalogVersion("tiltak-testcontainers")}",
        "org.testcontainers:kafka:${catalogVersion("tiltak-testcontainers")}",
        "org.testcontainers:postgresql:${catalogVersion("tiltak-testcontainers")}",
        "org.testcontainers:testcontainers:${catalogVersion("tiltak-testcontainers")}",
        "tools.jackson.core:jackson-core:${catalogVersion("tools-jackson")}",
        "tools.jackson.core:jackson-databind:${catalogVersion("tools-jackson")}",
        "tools.jackson.module:jackson-module-kotlin:${catalogVersion("tools-jackson")}",
    )
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
