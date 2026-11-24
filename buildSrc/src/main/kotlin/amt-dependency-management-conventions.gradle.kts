import org.gradle.api.artifacts.VersionCatalogsExtension

// Felles versjonsoverstyringer fra libs, også for transitive avhengigheter.
plugins {
    id("io.spring.dependency-management")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

extra["jackson-2-bom.version"] = versionCatalog.findVersion("jackson2-bom").get().requiredVersion
extra["jackson-bom.version"] = versionCatalog.findVersion("jackson3-bom").get().requiredVersion

dependencyManagement {
    dependencies {
        // Guava og komprimeringsbiblioteker.
        dependency("com.google.guava:guava:${versionCatalog.findVersion("guava").get().requiredVersion}")
        dependency("at.yawk.lz4:lz4-java:${versionCatalog.findVersion("lz4-java").get().requiredVersion}")
        dependency("org.xerial.snappy:snappy-java:${versionCatalog.findVersion("snappy-java").get().requiredVersion}")

        // OkHttp, inkludert JVM-artefaktet.
        dependency("com.squareup.okhttp3:okhttp:${versionCatalog.findVersion("okhttp").get().requiredVersion}")
        dependency("com.squareup.okhttp3:okhttp-jvm:${versionCatalog.findVersion("okhttp").get().requiredVersion}")

        // Override vulnerable versions selected by the Spring Boot BOM.
        val logbackVersion = versionCatalog.findVersion("logback").get().requiredVersion
        dependency("ch.qos.logback:logback-classic:$logbackVersion")
        dependency("ch.qos.logback:logback-core:$logbackVersion")
        dependency("org.json:json:${versionCatalog.findVersion("org-json").get().requiredVersion}")
    }
}
