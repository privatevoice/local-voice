plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.homenetwork.localvoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.homenetwork.localvoice"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // onn 4K Plus runs a 32-bit-only userspace.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Model files are already compressed ONNX; storing them uncompressed lets the
    // native engine read them straight from the APK.
    androidResources { noCompress += listOf("onnx", "txt") }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
