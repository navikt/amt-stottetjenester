plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(libs.shedlock.spring)
    api(project(":amt-tiltak:common:utils"))
    api(project(":amt-tiltak:common:db_utils"))
    api(libs.amt.lib.models)
    api(libs.nav.common.kafka) {
        exclude(group = "org.xerial.snappy", module = "snappy-java")
        exclude(group = "org.apache.avro", module = "avro")
        exclude(group = "io.confluent", module = "kafka-avro-serializer")
    }
    api(project(":amt-tiltak:kafka:kafka-config"))
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:kafka:kafka-producer"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.postgresql)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":amt-tiltak:db-migrations"))
    testImplementation("org.mockito:mockito-core")
    testImplementation(libs.mockito.kotlin)
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(project(":amt-tiltak:test:test-utils"))
    testImplementation(libs.mockk)
}
