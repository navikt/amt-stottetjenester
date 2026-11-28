plugins {
    id("amt-kotlin-library-conventions")
    id("amt-tiltak-test-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(project(":amt-tiltak:common:db_utils"))
    api(project(":amt-tiltak:data-publisher"))
    api(libs.shedlock.spring)
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    implementation("io.micrometer:micrometer-core")
    api(project(":amt-tiltak:clients:amt-arrangor-client"))
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:utils"))
    api(libs.unleash)
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.token.validation.spring)
    testImplementation(project(":amt-tiltak:db-migrations"))
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation(libs.kotest.runner.junit5)
}
