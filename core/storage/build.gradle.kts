plugins {
    alias(libs.plugins.traccia.android.library)
    alias(libs.plugins.traccia.hilt)
}

android {
    namespace = "it.traccia.core.storage"

    sourceSets {
        // Exported Room schemas, needed by MigrationTestHelper.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:geo"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
