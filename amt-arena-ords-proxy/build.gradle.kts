plugins {
    id("amt-spring-conventions")
}

group = "no.nav.amt-arena-ords-proxy"
version = "0.0.1"
description = "Proxy mot Arena ORDS i FSS"

dependencies {
    implementation(libs.nav.common.log)
    implementation(libs.nav.common.rest) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }

    implementation(libs.token.validation.spring)
    implementation(libs.okhttp.jvm)

    testImplementation(libs.mockwebserver)
}
