plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:application"))
    api(project(":amt-tiltak:arrangor"))
    api(project(":amt-tiltak:clients:amt-arrangor-client"))
    api(project(":amt-tiltak:clients:amt-person"))
    api(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:external-api"))
    api(project(":amt-tiltak:kafka:ansatt-ingestor"))
    api(project(":amt-tiltak:kafka:arena-acl-ingestor"))
    api(project(":amt-tiltak:kafka:arrangor-ingestor"))
    api(project(":amt-tiltak:kafka:kafka-config"))
    api(project(":amt-tiltak:navansatt"))
    api(project(":amt-tiltak:tilgangskontroll-tiltaksarrangor"))
    api(project(":amt-tiltak:tiltak"))
    api(libs.nav.common.kafka)
    api(libs.kafka.clients)
    api(libs.unleash)
    api(libs.testcontainers.core)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(project(":amt-tiltak:test:database"))
    api(project(":amt-tiltak:test:test-utils"))
    api(libs.kotest.assertions.core)
    api(libs.mockk)
    api("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    api(libs.mockwebserver)
    api(libs.mock.oauth2.server)
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("kafka-smoke")
    }
}

tasks.register<Test>("kafkaTest") {
    group = "verification"
    description = "Runs amt-tiltak smoke tests against a real Kafka broker."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mustRunAfter(tasks.named("test"))
    maxParallelForks = 1
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
    useJUnitPlatform {
        includeTags("kafka-smoke")
    }
}
