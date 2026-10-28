import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.KtlintExtension



// Felles oppsett for de frittstående Spring Boot-appene. Appspesifikke avhengigheter,
// ekstra repositories og kompilatorflagg hører hjemme i appens egen build.gradle.kts.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    constraints {
        implementation("com.google.guava:guava") {
            version {
                strictly(versionCatalog.findVersion("guava").get().requiredVersion)
            }
            because("Fixes GHSA-xxph-c9ww-hj94")
        }
        implementation("at.yawk.lz4:lz4-java") {
            version {
                strictly(versionCatalog.findVersion("lz4-java").get().requiredVersion)
            }
            because("Fixes CVE-2026-59949")
        }
        implementation("org.xerial.snappy:snappy-java") {
            version {
                strictly(versionCatalog.findVersion("snappy-java").get().requiredVersion)
            }
            because("Fixes CVE-2026-108106")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(versionCatalog.findLibrary("tools-jackson-module-kotlin").get())
    runtimeOnly("org.springframework.boot:spring-boot-starter-jetty")
    runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(versionCatalog.findLibrary("logstash-encoder").get())
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Appene kjøres via bootJar, ikke den vanlige jar-tasken.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
    extensions.configure<KtlintExtension> {
        version.set(versionCatalog.findVersion("ktlint-cli-version").get().requiredVersion)
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
}
