plugins {
    kotlin("jvm")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(versionCatalog.findLibrary("mockk").get())
    testImplementation(versionCatalog.findLibrary("kotest-assertions-core").get())
}
