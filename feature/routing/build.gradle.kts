plugins {
    alias(libs.plugins.traccia.android.feature)
}

android {
    namespace = "it.traccia.feature.routing"
}

dependencies {
    implementation(project(":core:geo"))
    implementation(project(":core:storage"))
    // BRouter routing engine (MIT, see libs/BROUTER-LICENSE.txt): routing-only build of release 1.7.10.
    api(files("libs/brouter-1.7.10-ro.jar"))
}
