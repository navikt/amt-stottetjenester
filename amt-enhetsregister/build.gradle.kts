plugins {
    id("amt-spring-database-conventions")
}

group = "no.nav.amt_enhetsregister"
version = "0.0.1-SNAPSHOT"
description = "ACL mot Brønnøysundregisteret"

repositories {
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    implementation(libs.nav.common.job)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.kafka) {
        exclude("org.apache.avro", "avro")
        exclude("org.xerial.snappy", "snappy-java")
        exclude("io.confluent", "kafka-avro-serializer")
    }
    implementation(libs.amt.lib.spring.boot)

    implementation(libs.shedlock.jdbc.template)

    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation(libs.mock.oauth2.server)
}
