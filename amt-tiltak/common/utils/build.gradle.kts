plugins {
    id("amt-tiltak-conventions")
}

dependencies {
    api(libs.caffeine)
    testImplementation(libs.tiltak.kotest.runner.junit5)
    testImplementation(libs.tiltak.kotest.assertions.core)
}
