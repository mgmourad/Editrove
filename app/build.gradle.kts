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
        versionCode = 3
        versionName = "0.3.0"

        // Static host of the published topic snapshots (SPEC §8). Override with
        // -Popenwire.dataUrl=https://example.org/ (e.g. your own GitHub Pages copy).
        val dataUrl = (project.findProperty("openwire.dataUrl") as String?) ?: "https://ansonlai.github.io/OpenWire/"
        buildConfigField("String", "DATA_BASE_URL", "\"$dataUrl\"")
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
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
