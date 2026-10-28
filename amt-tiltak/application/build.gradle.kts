import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
}

dependencies {
    api(project(":amt-tiltak:bff-tiltaksarrangor"))
    api(project(":amt-tiltak:bff-nav_ansatt"))
    api(project(":amt-tiltak:bff-internal"))
    api(project(":amt-tiltak:tiltak"))
    api(project(":amt-tiltak:tilgangskontroll-tiltaksarrangor"))
    api(project(":amt-tiltak:arrangor"))
    api(project(":amt-tiltak:clients:amt-person"))
    api(project(":amt-tiltak:external-api"))
    api(project(":amt-tiltak:db-migrations"))
    api(project(":amt-tiltak:kafka:kafka-config"))
    api(project(":amt-tiltak:kafka:kafka-producer"))
    api(project(":amt-tiltak:kafka:arena-acl-ingestor"))
    api(project(":amt-tiltak:kafka:gjennomforing-ingestor"))
    api(project(":amt-tiltak:kafka:arrangor-ingestor"))
    api(project(":amt-tiltak:kafka:ansatt-ingestor"))
    api(project(":amt-tiltak:kafka:nav-bruker-ingestor"))
    api(project(":amt-tiltak:kafka:nav-ansatt-ingestor"))
    api(project(":amt-tiltak:kafka:deltaker-ingestor"))
    api(project(":amt-tiltak:clients:mulighetsrommet-api-client"))
    api(libs.nav.common.token.client)
    api(libs.tiltak.poao.tilgang.client) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(libs.nav.common.log)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(libs.tiltak.unleash)
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-logging")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-data-jdbc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api(libs.token.validation.spring)
    api(libs.shedlock.jdbc.template)
    api(libs.tools.jackson.module.kotlin)
    api("org.flywaydb:flyway-core")
    api("org.springframework.boot:spring-boot-flyway")
    api("org.flywaydb:flyway-database-postgresql")
    api("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(libs.postgresql)
    api(libs.logstash.encoder)
    api(libs.shedlock.spring)
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

tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("amt-tiltak")
    archiveClassifier.set("app")
    mainClass.set("no.nav.amt.tiltak.application.ApplicationKt")
}
