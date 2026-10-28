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
        dependency("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
        dependency("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
    }
}

dependencies {
    implementation(libs.nimbus.oauth2.oidc.sdk)
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

    implementation(libs.unleash)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation(libs.amt.lib.models)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.kotest.extensions.testcontainers)
    testImplementation(libs.token.validation.spring.test)

    testImplementation("org.testcontainers:testcontainers-kafka")
}
