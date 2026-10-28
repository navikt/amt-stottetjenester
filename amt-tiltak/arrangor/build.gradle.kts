dependencies {
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:tilgangskontroll-tiltaksarrangor"))
    api(project(":amt-tiltak:clients:amt-person"))
    api(project(":amt-tiltak:common:auth"))
    api(project(":amt-tiltak:data-publisher"))
    api(libs.token.validation.spring)
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api(libs.postgresql)
    api(project(":amt-tiltak:common:db_utils"))
    api("io.micrometer:micrometer-core")
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.mock.oauth2.server)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.tiltak.kotest.runner.junit5)
    testImplementation(libs.tiltak.kotest.assertions.core)
    api(project(":amt-tiltak:test:test-utils"))
    testImplementation(libs.mockk)
}
