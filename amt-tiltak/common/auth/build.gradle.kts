plugins {
    id("amt-kotlin-library-conventions")
    id("amt-tiltak-test-conventions")
}

dependencies {
    api(libs.token.validation.spring)
    api(libs.nav.common.audit.log)
    api("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(project(":amt-tiltak:core"))
    testImplementation(libs.mock.oauth2.server)
}
