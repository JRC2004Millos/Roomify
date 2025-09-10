plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Si NO usas Compose en MedicionActivity puedes dejar compose, no estorba
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.roomify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roomify"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)

    // CameraX (ok)
    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // Compose
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ✅ usa ARSceneView (0.9.7 para que case con el código que te pasé)
    implementation("io.github.sceneview:arsceneview:0.9.7")
    implementation ("com.google.code.gson:gson:2.10.1")

    // ✅ ARCore (UNA sola vez; deja la más reciente estable)
    implementation("com.google.ar:core:1.45.0")

    implementation ("com.google.android.material:material:1.12.0")


    // OpenCV como módulo local (ok)
    implementation(project(":sdk"))
}
