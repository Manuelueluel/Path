plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.gpx"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:storage"))
    implementation(project(":core:terrain"))
    implementation(project(":feature:elevation"))

    // Android's XmlPullParser is kxml2: the same parser for JVM unit tests.
    testImplementation(libs.kxml2)
}
