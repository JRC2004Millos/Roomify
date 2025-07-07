plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.procesamiento3d"
    compileSdk = 35

    defaultConfig {
        //applicationId = "com.example.procesamiento3d"
        minSdk = 26
        targetSdk = 35
        //versionCode = 1
        //versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets["main"].java.srcDirs("src/main/java", "libs")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(files("libs/opencv-480.jar"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(project(":sdk"))
    implementation(libs.junit.junit)
    implementation("io.github.sceneview:arsceneview:0.9.7")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files("libs/opencv-480.jar"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}