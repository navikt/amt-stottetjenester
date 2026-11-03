import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Felles oppsett for bibliotekmodulene i amt-tiltak, uten Spring Boot-pluginens pakketering.

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("amt-dependency-management-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "no.nav.amt-tiltak"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

val javaVersion = versionCatalog.findVersion("java.version").get().requiredVersion
kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Bibliotekmodulene importerer Spring Boot-BOM-en eksplisitt og bruker Kotlin-versjonen fra libs.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${versionCatalog.findVersion("spring-boot").get().requiredVersion}") {
            bomProperty("kotlin.version", versionCatalog.findVersion("kotlin").get().requiredVersion)
        }
    }
}

dependencies {
    // JUnit Platform ved testkjøring.
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Noen moduler har testhjelpere, men ingen egne tester.
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
}
