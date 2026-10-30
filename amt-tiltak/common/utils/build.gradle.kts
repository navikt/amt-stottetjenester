plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(libs.caffeine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
