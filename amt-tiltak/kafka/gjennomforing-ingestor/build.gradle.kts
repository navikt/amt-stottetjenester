plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    testImplementation(libs.kotest.assertions.core)
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}
