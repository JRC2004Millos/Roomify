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
    }
}

rootProject.name = "Roomify"

// Módulos principales
include(":app")
include(":sdk")
include(":unityLibrary")
project(":unityLibrary").projectDir = file("unityLibrary")

// Configuración de SDK
val opencvsdk = file("sdk")
project(":sdk").projectDir = opencvsdk

// Unity Library está en la ubicación por defecto (./unityLibrary)