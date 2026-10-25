plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.planner"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:storage"))
    implementation(project(":feature:routing"))
    implementation(project(":feature:elevation"))
    implementation(libs.maplibre.android)
}
