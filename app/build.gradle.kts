plugins {
    alias(libs.plugins.traccia.android.application)
    alias(libs.plugins.traccia.android.compose)
    alias(libs.plugins.traccia.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.traccia.app"

    defaultConfig {
        applicationId = "it.traccia"
        versionCode = 1
        versionName = "0.1.0"
        // Empty: use the catalogUrl of map_sources.json. Override for testing with -Ptraccia.catalogUrl=...
        buildConfigField("String", "CATALOG_URL", "\"${providers.gradleProperty("traccia.catalogUrl").getOrElse("")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:geo"))
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))
    implementation(project(":core:terrain"))

    implementation(project(":feature:map"))
    implementation(project(":feature:offlinemaps"))
    implementation(project(":feature:heatmap"))
    implementation(project(":feature:planner"))
    implementation(project(":feature:routing"))
    implementation(project(":feature:elevation"))
    implementation(project(":feature:gpx"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
}
