plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.library"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:storage"))
    implementation(project(":feature:gpx"))
    implementation(project(":feature:planner"))
}
