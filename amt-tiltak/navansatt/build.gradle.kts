dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:clients:amt-person"))
    api(project(":amt-tiltak:common:auth"))
    api(project(":amt-tiltak:common:db_utils"))
    api(project(":amt-tiltak:data-publisher"))
    api(libs.token.validation.spring)
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.postgresql)
    api(libs.caffeine)
    api(libs.shedlock.spring)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.mock.oauth2.server)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":amt-tiltak:db-migrations"))
    testImplementation("org.mockito:mockito-core")
    testImplementation(libs.mockito.kotlin)
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.tiltak.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.tiltak.kotest.runner.junit5)
    testImplementation(libs.tiltak.kotest.assertions.core)
    api("io.micrometer:micrometer-registry-prometheus")
}
