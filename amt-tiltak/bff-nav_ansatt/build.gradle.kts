plugins {
    id("amt-kotlin-library-conventions")
    id("amt-tiltak-test-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:auth"))
    api("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(project(":amt-tiltak:tilgangskontroll-tiltaksansvarlig"))
    api(libs.unleash)
    api(libs.token.validation.spring)
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(project(":amt-tiltak:test:database"))
}
