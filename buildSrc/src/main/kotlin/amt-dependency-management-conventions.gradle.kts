import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("io.spring.dependency-management")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    dependencies {
        dependency("org.apache.kafka:kafka-clients:${versionCatalog.findVersion("kafka-clients").get().requiredVersion}")
        dependency("com.google.guava:guava:${versionCatalog.findVersion("guava").get().requiredVersion}")
        dependency("at.yawk.lz4:lz4-java:${versionCatalog.findVersion("lz4-java").get().requiredVersion}")
        dependency("org.xerial.snappy:snappy-java:${versionCatalog.findVersion("snappy-java").get().requiredVersion}")
        dependency("com.squareup.okhttp3:okhttp:${versionCatalog.findVersion("okhttp").get().requiredVersion}")
        dependency("com.squareup.okhttp3:okhttp-jvm:${versionCatalog.findVersion("okhttp").get().requiredVersion}")
    }
}
