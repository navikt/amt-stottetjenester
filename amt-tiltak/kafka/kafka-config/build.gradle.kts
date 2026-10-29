plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:json"))
    api(libs.nav.common.kafka) {
        exclude(group = "org.xerial.snappy", module = "snappy-java")
        exclude(group = "org.apache.avro", module = "avro")
        exclude(group = "io.confluent", module = "kafka-avro-serializer")
    }
    api("org.springframework.boot:spring-boot-starter")
    api(libs.shedlock.jdbc.template)
    api("io.micrometer:micrometer-core")
    testImplementation(libs.postgresql)
    testImplementation(libs.tiltak.testcontainers.kafka)
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.tiltak.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(project(":amt-tiltak:test:database"))
}
