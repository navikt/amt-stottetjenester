plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:clients:amt-person"))
    api(project(":amt-tiltak:clients:amt-arrangor-client"))
    api(project(":amt-tiltak:navansatt"))
    api(project(":amt-tiltak:common:auth"))
    api(project(":amt-tiltak:common:db_utils"))
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.postgresql)
    api(libs.caffeine)
    api(libs.shedlock.spring)
    api("io.micrometer:micrometer-registry-prometheus")
    api(libs.unleash)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(libs.nav.common.kafka) {
        exclude(group = "org.xerial.snappy", module = "snappy-java")
        exclude(group = "org.apache.avro", module = "avro")
        exclude(group = "io.confluent", module = "kafka-avro-serializer")
    }
    api(project(":amt-tiltak:common:utils"))
    api(libs.poao.tilgang.client) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(project(":amt-tiltak:data-publisher"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.mock.oauth2.server)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":amt-tiltak:db-migrations"))
    testImplementation("org.mockito:mockito-core")
    testImplementation(libs.mockito.kotlin)
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(project(":amt-tiltak:arrangor"))
    testImplementation(libs.token.validation.spring)
    testImplementation(project(":amt-tiltak:test:test-utils"))
    testImplementation(libs.mockk)
}
