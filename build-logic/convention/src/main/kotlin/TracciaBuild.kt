import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal object TracciaBuild {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 26
    val javaVersion = JavaVersion.VERSION_17
    val jvmTarget = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).orElseThrow { IllegalStateException("Missing library alias '$alias' in libs.versions.toml") }

/** Common Android setup shared by application and library modules. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = TracciaBuild.COMPILE_SDK
        defaultConfig.minSdk = TracciaBuild.MIN_SDK
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = TracciaBuild.javaVersion
        compileOptions.targetCompatibility = TracciaBuild.javaVersion
        testOptions.unitTests.all { it.useJUnitPlatform() }
    }
    configureKotlin()
    configureUnitTests()
    dependencies {
        add("androidTestImplementation", libs.lib("androidx-test-runner"))
        add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
    }
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(TracciaBuild.jvmTarget)
            allWarningsAsErrors.set(false)
        }
    }
}

internal fun Project.configureUnitTests() {
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
