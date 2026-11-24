plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    api(libs.okhttp.jvm)
    api(libs.nav.common.token.client)
    api(project(":amt-tiltak:common:json"))
    implementation(libs.nav.common.rest)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotest.runner.junit5)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
