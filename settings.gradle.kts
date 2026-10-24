pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Traccia"

include(":app")

include(":core:common")
include(":core:geo")
include(":core:storage")
include(":core:ui")

include(":feature:map")
include(":feature:offlinemaps")
include(":feature:heatmap")
include(":feature:planner")
include(":feature:routing")
include(":feature:elevation")
include(":feature:gpx")
include(":feature:library")
include(":feature:settings")
