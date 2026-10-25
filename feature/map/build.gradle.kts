plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.map"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:terrain"))
    implementation(project(":feature:heatmap"))
    implementation(project(":feature:planner"))
    implementation(project(":feature:gpx"))
    implementation(project(":core:storage"))
    implementation(libs.maplibre.android)
    implementation(libs.androidx.datastore.preferences)
}
