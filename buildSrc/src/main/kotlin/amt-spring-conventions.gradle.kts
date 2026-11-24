import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Felles oppsett for de frittstående Spring Boot-appene. Appspesifikke avhengigheter,
// ekstra repositories og kompilatorflagg hører hjemme i appens egen build.gradle.kts.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("amt-dependency-management-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    // HTTP-server med Jetty i stedet for Tomcat.
    implementation("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    runtimeOnly("org.springframework.boot:spring-boot-starter-jetty")

    // JSON og validering.
    implementation(versionCatalog.findLibrary("tools-jackson-module-kotlin").get())
    runtimeOnly("org.springframework.boot:spring-boot-starter-validation")

    // Helseendepunkter, metrikker og strukturert logging.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(versionCatalog.findLibrary("logstash-encoder").get())

    // Felles testgrunnlag. HTTP-testklient og mocking deklareres i appene som bruker dem.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val javaVersion = versionCatalog.findVersion("java.version").get().requiredVersion
kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Appene kjøres via bootJar, ikke den vanlige jar-tasken.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}

// Tilpass ktlint bare i appene som bruker pluginen.
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
