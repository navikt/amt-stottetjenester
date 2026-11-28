plugins {
    id("amt-spring-database-conventions")
    id("amt-spring-test-conventions")
}

group = "no.nav.amt_enhetsregister"
version = "0.0.1-SNAPSHOT"
description = "ACL mot Brønnøysundregisteret"

repositories {
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation(libs.nav.common.job)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.kafka) {
        exclude("org.xerial.snappy", "snappy-java")
    }
    implementation(libs.amt.lib.spring.boot)

    implementation(libs.shedlock.jdbc.template)

    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation(libs.mock.oauth2.server)
}
