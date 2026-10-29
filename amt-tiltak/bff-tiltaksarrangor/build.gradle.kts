plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api(project(":amt-tiltak:common:auth"))
    api("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    api("org.springframework.boot:spring-boot-starter-jetty")
    api(libs.token.validation.spring)
}
