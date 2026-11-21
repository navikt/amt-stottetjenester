plugins {
    id("amt-spring-database-conventions")
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
        exclude(group = "org.apache.avro", module = "avro")
        exclude(group = "io.confluent", module = "kafka-avro-serializer")
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

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("kafka-smoke")
    }
}

tasks.register<Test>("kafkaTest") {
    group = "verification"
    description = "Runs amt-arena-acl smoke tests against a real Kafka broker."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mustRunAfter(tasks.named("test"))
    maxParallelForks = 1
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
    useJUnitPlatform {
        includeEngines("junit-jupiter")
        includeTags("kafka-smoke")
    }
}
