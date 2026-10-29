plugins {
    id("amt-tiltak-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api(project(":amt-tiltak:common:json"))
    api(project(":amt-tiltak:core"))
}
