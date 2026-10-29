plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:auth"))
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(project(":amt-tiltak:tilgangskontroll-tiltaksansvarlig"))
    api(libs.tiltak.unleash)
    api(libs.token.validation.spring)
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation(libs.mockito.kotlin)
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.tiltak.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.tiltak.kotest.assertions.core)
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(libs.mockk)
}
