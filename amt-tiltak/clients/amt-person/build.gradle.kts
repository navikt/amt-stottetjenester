plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    implementation("org.springframework:spring-context")
    api(libs.okhttp.jvm)
    api(libs.nav.common.token.client)
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:json"))
    implementation(libs.nav.common.rest)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotest.runner.junit5)
}
