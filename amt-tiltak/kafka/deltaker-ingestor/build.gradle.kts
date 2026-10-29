plugins {
    id("amt-tiltak-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    api(project(":amt-tiltak:kafka:gjennomforing-ingestor"))
    api(libs.tiltak.unleash)
}
