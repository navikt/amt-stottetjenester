plugins {
    id("amt-kotlin-library-conventions")
}

dependencies {
    api(libs.tools.jackson.module.kotlin)
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}
