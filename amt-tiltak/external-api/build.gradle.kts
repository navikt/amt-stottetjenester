plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:auth"))
    api(libs.nav.common.job) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(libs.token.validation.spring)
    testImplementation(project(":amt-tiltak:test:test-utils"))
    testImplementation(libs.tiltak.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
