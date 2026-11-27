plugins {
    java
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("kafka-smoke")
    }
}

tasks.register<Test>("kafkaTest") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mustRunAfter(tasks.named("test"))
    maxParallelForks = 1
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
    useJUnitPlatform {
        includeTags("kafka-smoke")
    }
}
