plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(project(":amt-tiltak:core"))
    api("org.springframework:spring-jdbc")
    implementation(project(":amt-tiltak:clients:amt-person"))
    implementation(project(":amt-tiltak:common:db_utils"))
    implementation(project(":amt-tiltak:data-publisher"))
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":amt-tiltak:test:database"))
    testImplementation(libs.kotest.assertions.core)
}
