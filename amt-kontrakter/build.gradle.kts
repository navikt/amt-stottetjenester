plugins {
    id("amt-kotlin-library-conventions")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "no.nav.amt"

ktlint {
    version.set(libs.versions.ktlint.cli.version.get())
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
