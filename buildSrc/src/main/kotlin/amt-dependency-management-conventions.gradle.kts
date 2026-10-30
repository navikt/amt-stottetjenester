import org.gradle.api.artifacts.VersionCatalogsExtension

// Felles versjonsoverstyringer fra libs, også for transitive avhengigheter.
plugins {
    id("io.spring.dependency-management")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    dependencies {
        // Kafka-klient.
        dependency("org.apache.kafka:kafka-clients:${versionCatalog.findVersion("kafka-clients").get().requiredVersion}")

        // Guava og komprimeringsbiblioteker.
        dependency("com.google.guava:guava:${versionCatalog.findVersion("guava").get().requiredVersion}")
        dependency("at.yawk.lz4:lz4-java:${versionCatalog.findVersion("lz4-java").get().requiredVersion}")
        dependency("org.xerial.snappy:snappy-java:${versionCatalog.findVersion("snappy-java").get().requiredVersion}")

        // OkHttp, inkludert JVM-artefaktet.
        dependency("com.squareup.okhttp3:okhttp:${versionCatalog.findVersion("okhttp").get().requiredVersion}")
        dependency("com.squareup.okhttp3:okhttp-jvm:${versionCatalog.findVersion("okhttp").get().requiredVersion}")
    }
}
