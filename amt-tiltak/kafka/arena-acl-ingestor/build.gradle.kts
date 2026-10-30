plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.postgresql)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    api(project(":amt-tiltak:kafka:gjennomforing-ingestor"))
    api(project(":amt-tiltak:clients:amt-person"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(project(":amt-tiltak:tiltak"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(project(":amt-tiltak:arrangor"))
}
