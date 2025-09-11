pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs("$rootDir/unityLibrary/libs")
        }
    }
}

rootProject.name = "Roomify"

val opencvsdk = file("sdk")

include(":app")
include(":procesamiento3d")
include(":unityLibrary")
include(":sdk")
include(":unityLibrary:xrmanifest.androidlib")

project(":sdk").projectDir = opencvsdk