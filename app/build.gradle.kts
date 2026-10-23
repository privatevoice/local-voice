import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing key lives outside the repo. Point LOCAL_VOICE_SIGNING at a properties file
// with storeFile/storePassword/keyAlias/keyPassword; without it, release builds use the debug key.
val signingProps = (System.getenv("LOCAL_VOICE_SIGNING")
    ?: "${System.getProperty("user.home")}/dev/local-voice-signing/signing.properties")
    .let(::File)
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "dev.homenetwork.localvoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.homenetwork.localvoice"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }

    // One APK per CPU type: 32-bit for boxes like the onn 4K, 64-bit for most others.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
