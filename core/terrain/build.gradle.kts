plugins {
    alias(libs.plugins.traccia.android.library)
    alias(libs.plugins.traccia.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.traccia.core.terrain"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:geo"))
    api(project(":core:storage"))
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
