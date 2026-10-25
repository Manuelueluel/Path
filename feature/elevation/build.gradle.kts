plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.elevation"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:terrain"))
}
