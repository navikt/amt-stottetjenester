plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://packages.confluent.io/maven/")
}

dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.22.4")
    }
}

dependencies {
    constraints {
        implementation("at.yawk.lz4:lz4-java") {
            version { strictly("1.11.4") }
            because("Fixes CVE-2026-59949")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    runtimeOnly("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(libs.tools.jackson.module.kotlin)
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    implementation(libs.amt.lib.utils)
    implementation(libs.amt.lib.spring.boot)
    implementation(libs.nav.common.kafka)

    runtimeOnly(libs.logstash.encoder)
    implementation(libs.kafka.avro.serializer)

    implementation(libs.poao.tilgang.client)

    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.amt.lib.testing)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled",
        )
    }
}

ktlint {
    version = libs.versions.ktlint.cli.version.get()
}

configurations.named("ktlint") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            requested.version?.let {
                useVersion(it)
                because("ktlint must use the Kotlin versions it was compiled against")
            }
        }
    }
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

tasks.named("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
