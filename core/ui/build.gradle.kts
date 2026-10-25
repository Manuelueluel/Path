plugins {
    alias(libs.plugins.traccia.android.library)
    alias(libs.plugins.traccia.android.compose)
    alias(libs.plugins.traccia.hilt)
}

android {
    namespace = "it.traccia.core.ui"
}

dependencies {
    api(project(":core:common"))
    api(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
