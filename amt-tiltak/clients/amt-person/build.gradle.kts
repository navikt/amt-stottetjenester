dependencies {
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(libs.okhttp.jvm)
    api(libs.nav.common.token.client)
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:json"))
    api(libs.nav.common.rest)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.mockwebserver)
    testImplementation(libs.tiltak.kotest.runner.junit5)
}
