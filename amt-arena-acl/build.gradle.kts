plugins {
    id("amt-spring-database-conventions")
    id("amt-kafka-test-conventions")
}

group = "no.nav.amt.arena-acl"
version = "1.0-SNAPSHOT"

// Bevarer artefaktnavnet fra da prosjektet het "arena-acl" i egen Gradle-build.
base {
    archivesName = "arena-acl"
}

repositories {
    maven("https://packages.confluent.io/maven/")
}

val navCommonModules =
    listOf(
        libs.nav.common.log,
        libs.nav.common.job,
        libs.nav.common.rest,
        libs.nav.common.token.client,
    )

dependencyManagement {
    dependencies {
        dependency("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
    }
}

dependencies {
    implementation(libs.shedlock.jdbc.template)

    implementation(libs.token.validation.spring)

    navCommonModules.forEach {
        implementation(it)
    }

    implementation(libs.nav.common.kafka) {
        exclude(group = "org.xerial.snappy", module = "snappy-java")
    }

    implementation(libs.amt.lib.models)

    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.kotest.extensions.testcontainers)
    testImplementation(libs.token.validation.spring.test)

    testImplementation("org.testcontainers:testcontainers-kafka")
}

tasks.named<Test>("kafkaTest") {
    description = "Runs amt-arena-acl smoke tests against a real Kafka broker."
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
}
