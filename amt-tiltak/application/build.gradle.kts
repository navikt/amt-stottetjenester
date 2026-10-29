import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("amt-spring-database-conventions")
}

group = "no.nav.amt-tiltak"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":amt-tiltak:bff-tiltaksarrangor"))
    implementation(project(":amt-tiltak:bff-nav_ansatt"))
    implementation(project(":amt-tiltak:bff-internal"))
    implementation(project(":amt-tiltak:tiltak"))
    implementation(project(":amt-tiltak:tilgangskontroll-tiltaksarrangor"))
    implementation(project(":amt-tiltak:arrangor"))
    implementation(project(":amt-tiltak:clients:amt-person"))
    implementation(project(":amt-tiltak:external-api"))
    implementation(project(":amt-tiltak:db-migrations"))
    implementation(project(":amt-tiltak:kafka:kafka-config"))
    implementation(project(":amt-tiltak:kafka:kafka-producer"))
    implementation(project(":amt-tiltak:kafka:arena-acl-ingestor"))
    implementation(project(":amt-tiltak:kafka:gjennomforing-ingestor"))
    implementation(project(":amt-tiltak:kafka:arrangor-ingestor"))
    implementation(project(":amt-tiltak:kafka:ansatt-ingestor"))
    implementation(project(":amt-tiltak:kafka:nav-bruker-ingestor"))
    implementation(project(":amt-tiltak:kafka:nav-ansatt-ingestor"))
    implementation(project(":amt-tiltak:kafka:deltaker-ingestor"))
    implementation(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    implementation(libs.nav.common.token.client)
    implementation(libs.tiltak.poao.tilgang.client) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    implementation(libs.nav.common.log)
    implementation(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    implementation(libs.tiltak.unleash)
    implementation(libs.token.validation.spring)
    implementation(libs.shedlock.jdbc.template)
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation(libs.shedlock.spring)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(libs.mockk)
}

springBoot {
    mainClass.set("no.nav.amt.tiltak.application.ApplicationKt")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveBaseName.set("amt-tiltak")
    archiveClassifier.set("")
}

tasks.named<Test>("test") {
    failOnNoDiscoveredTests = false
}

tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("amt-tiltak")
    archiveClassifier.set("app")
    mainClass.set("no.nav.amt.tiltak.application.ApplicationKt")
}
