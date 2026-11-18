import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "news.openwire"
    compileSdk = 37

    defaultConfig {
        applicationId = "news.editrove"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "0.8.0-beta.1"
        // Editrove's built-in voices (sherpa-onnx) ship native code for ARM phones and tablets.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // Static host of the published topic snapshots (SPEC §8). Override with
        // -Popenwire.dataUrl=https://example.org/ (e.g. your own GitHub Pages copy).
        // Editrove's own GitHub Pages feed (rebuilt every 30 minutes by .github/workflows/pages.yml).
        val dataUrl = (project.findProperty("openwire.dataUrl") as String?) ?: "https://mgmourad.github.io/Editrove/"
        buildConfigField("String", "DATA_BASE_URL", "\"$dataUrl\"")
        // Self-update from GitHub Releases. Store builds (e.g. F-Droid) pass -Popenwire.selfUpdate=false.
        val selfUpdate = (project.findProperty("openwire.selfUpdate") as String?)?.toBoolean() ?: true
        buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
        buildConfigField("String", "UPDATE_REPO", "\"mgmourad/Editrove\"")
    }

    // Release signing: never committed. Looked up in <repo>/signing/keystore.properties,
    // then ~/.openwire/keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
    val keystoreProps = listOf(
        rootProject.file("signing/keystore.properties"),
        file("${System.getProperty("user.home")}/.openwire/keystore.properties"),
    ).firstOrNull { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Native libraries stored compressed (the speech runtime is large; this halves its share of the APK).
    packaging { jniLibs { useLegacyPackaging = true } }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The in-app changelog reads the same per-version notes the store listing uses.
    sourceSets["main"].assets.srcDir("../fastlane/metadata/android/en-US/changelogs")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":notifications"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.browser)
    // Live TV (HLS) and radio, with background playback and lock-screen controls.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    // Built-in voices for phones without Google's speech engine (Huawei etc.): sherpa-onnx (Apache-2.0),
    // https://github.com/k2-fsa/sherpa-onnx v1.13.8, sha256 b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471.
    // (1.x: 2.0 dropped the built-in espeak-ng phonemizer that Piper voices need.)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))
    // Unpacks the downloaded voices (.tar.bz2).
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
