pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")  // Corregido: agregado asterisco
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
        // Agregado flatDir aquí en lugar de duplicar dependencyResolutionManagement
        flatDir {
            dirs("unityLibrary/libs")  // Simplificado la ruta
        }
    }
}

rootProject.name = "Roomify"

// Módulos principales
include(":app")
include(":sdk")
include(":procesamiento3d")  // Agregar módulo faltante
include(":unityLibrary")
project(":unityLibrary").projectDir = file("unityLibrary")

// Submódulo de Unity XR
include(":unityLibrary:xrmanifest.androidlib")
project(":unityLibrary:xrmanifest.androidlib").projectDir = file("unityLibrary/xrmanifest.androidlib")

// Configuración de SDK
val opencvsdk = file("sdk")
project(":sdk").projectDir = opencvsdk