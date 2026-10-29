plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(libs.caffeine)
    testImplementation(libs.tiltak.kotest.runner.junit5)
    testImplementation(libs.tiltak.kotest.assertions.core)
}
