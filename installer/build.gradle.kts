import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Same signing setup as :app (see app/build.gradle.kts).
val signingProps = (System.getenv("LOCAL_VOICE_SIGNING")
    ?: "${System.getProperty("user.home")}/dev/local-voice-signing/signing.properties")
    .let(::File)
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

// "Local Voice Setup": carries the Local Voice APK and installs it through a PackageInstaller
// session. Android 13+ marks apps opened from a downloaded file (e.g. via Downloader) as
// "restricted" and Android TV has no switch to lift that, which leaves the accessibility
// service impossible to turn on. Session installs are not restricted.
val abis = mapOf("armeabiV7a" to "armeabi-v7a", "arm64V8a" to "arm64-v8a")

android {
    namespace = "dev.homenetwork.localvoice.installer"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.homenetwork.localvoice.installer"
        minSdk = 24
        targetSdk = 34
        // Keep in step with app/build.gradle.kts.
        versionCode = 5
        versionName = "0.1.4"
    }

    // One installer per CPU type, each carrying the matching Local Voice APK.
    flavorDimensions += "abi"
    productFlavors {
        abis.keys.forEach { create(it) { dimension = "abi" } }
    }
    abis.forEach { (flavor, abi) ->
        sourceSets[flavor].assets.srcDir(layout.buildDirectory.dir("bundled/$abi"))
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

    // The bundled APK is already a zip; storing it as-is keeps the copy fast.
    androidResources { noCompress += "apk" }
}

val bundleTasks = abis.map { (flavor, abi) ->
    tasks.register<Copy>("copyLocalVoice${flavor.replaceFirstChar(Char::uppercase)}") {
        dependsOn(":app:assembleRelease")
        from(project(":app").layout.buildDirectory.file("outputs/apk/release/app-$abi-release.apk"))
        into(layout.buildDirectory.dir("bundled/$abi"))
        rename { "local-voice.apk" }
    }
}
tasks.named("preBuild") { dependsOn(bundleTasks) }
