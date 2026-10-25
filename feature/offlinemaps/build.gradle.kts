plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.offlinemaps"
}

dependencies {
    implementation(project(":core:storage"))
    implementation(project(":core:terrain"))
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.okhttp.mockwebserver)
}
