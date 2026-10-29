plugins {
    id("amt-tiltak-conventions")
}

dependencies {
    api(project(":amt-tiltak:application"))
    api(libs.tiltak.testcontainers)
    api(libs.tiltak.testcontainers.postgresql)
    api(libs.tiltak.testcontainers.kafka)
    api(project(":amt-tiltak:test:database"))
    api(project(":amt-tiltak:test:test-utils"))
    api(libs.tiltak.kotest.assertions.core)
    api(libs.tiltak.kotest.framework.concurrency)
    api("org.springframework.boot:spring-boot-starter-test")
    api(libs.mockwebserver)
    api(libs.mock.oauth2.server)
}
