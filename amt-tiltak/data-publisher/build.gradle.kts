plugins {
    id("amt-kotlin-library-conventions")
    id("amt-tiltak-test-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(libs.shedlock.spring)
    api(project(":amt-tiltak:common:utils"))
    api(project(":amt-tiltak:common:db_utils"))
    api(libs.amt.lib.models)
    api(libs.nav.common.kafka) {
        exclude(group = "org.xerial.snappy", module = "snappy-java")
    }
    api(project(":amt-tiltak:kafka:kafka-config"))
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:kafka:kafka-producer"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.postgresql)
    testImplementation(project(":amt-tiltak:db-migrations"))
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(project(":amt-tiltak:test:test-utils"))
}
