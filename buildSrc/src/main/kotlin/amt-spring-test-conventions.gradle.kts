plugins {
    kotlin("jvm")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation(versionCatalog.findLibrary("mockk").get())
    testImplementation(versionCatalog.findLibrary("springmockk").get())
}
