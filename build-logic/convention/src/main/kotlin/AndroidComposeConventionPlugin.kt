import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Enables Jetpack Compose on an Android module that already applies AGP. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.lib("compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.lib("compose-ui"))
                add("implementation", libs.lib("compose-ui-graphics"))
                add("implementation", libs.lib("compose-ui-tooling-preview"))
                add("implementation", libs.lib("compose-material3"))
                add("implementation", libs.lib("compose-material-icons-core"))
                add("debugImplementation", libs.lib("compose-ui-tooling"))
            }
        }
}
