import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Android library + Compose + Hilt + navigation, with access to the core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("traccia.android.library")
            pluginManager.apply("traccia.android.compose")
            pluginManager.apply("traccia.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                add("implementation", project(":core:common"))
                add("implementation", project(":core:ui"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
            }
        }
}
