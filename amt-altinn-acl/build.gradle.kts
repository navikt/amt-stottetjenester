plugins {
    id("amt-spring-database-conventions")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "no.nav.amt-altinn-acl"
version = "0.0.1-SNAPSHOT"

dependencies {
    // Spring Boot, HTTP-klient og sikkerhet
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Applikasjonsfunksjoner og serialisering
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Nav-biblioteker
    implementation(libs.amt.lib.utils)
    implementation(libs.amt.lib.spring.boot)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.job)

    // Spring Boot-teststøtte
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    // Assertions og coroutines i tester
    testImplementation(libs.kotest.assertions.core)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
