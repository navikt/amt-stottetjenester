dependencies {
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.postgresql)
    api(project(":amt-tiltak:common:db_utils"))
    api(project(":amt-tiltak:data-publisher"))
    api(libs.shedlock.spring)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api("io.micrometer:micrometer-registry-prometheus")
    api(project(":amt-tiltak:clients:amt-arrangor-client"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:utils"))
    api(libs.tiltak.unleash)
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.token.validation.spring)
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
    testImplementation(libs.mockk)
}
