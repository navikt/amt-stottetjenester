plugins {
    id("amt-spring-database-conventions")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    maven("https://packages.confluent.io/maven/")
}

dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:${libs.versions.jackson2.bom.get()}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(libs.amt.lib.utils)
    implementation(libs.amt.lib.spring.boot)
    implementation(libs.nav.common.kafka)

    implementation(libs.kafka.avro.serializer)

    implementation(libs.poao.tilgang.client)

    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)

    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.amt.lib.testing)
}

tasks.register<GenerateAvroTask>("generateAvroJava") {
    description = "Genererer Java-klasser fra Avro-skjemaer"
    avroSchemasDir = layout.projectDirectory.dir("src/main/avro")
    avroCodeGenerationDir = layout.buildDirectory.dir("generated/avro/java")
}

sourceSets.named("main") {
    java.srcDir(
        tasks
            .named<GenerateAvroTask>("generateAvroJava")
            .flatMap { it.avroCodeGenerationDir },
    )
}

tasks.named("compileKotlin") {
    dependsOn("generateAvroJava")
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("generateAvroJava")
}

tasks.named("runKtlintFormatOverMainSourceSet") {
    dependsOn("generateAvroJava")
}
