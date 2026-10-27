plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.settings"
}

dependencies {
    implementation(project(":core:storage"))
    implementation(project(":feature:map"))
    implementation(project(":feature:heatmap"))
    implementation(project(":feature:elevation"))
}
