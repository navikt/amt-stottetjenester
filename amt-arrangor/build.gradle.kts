plugins {
    id("amt-spring-database-conventions")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    // Kafka
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation(libs.kafka.clients) {
        exclude("org.xerial.snappy", "snappy-java")
    }

    // Utgående HTTP-kall og OAuth2 (TokenX/Azure AD client credentials)
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Serialisering og Kotlin-coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Nav-interne biblioteker
    implementation(libs.nav.common.log)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.job)
    implementation(libs.nav.common.kafka)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)
    implementation(libs.amt.lib.utils)
    implementation(libs.amt.lib.spring.boot)

    // Test – Spring Boot testoppsett (JDBC, RestClient, MVC)
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    // Test – autentisering og assertions
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.kotest.assertions.core)
}
