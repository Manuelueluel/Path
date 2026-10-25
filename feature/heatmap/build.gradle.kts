plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.heatmap"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:terrain"))
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.okhttp.mockwebserver)
}
