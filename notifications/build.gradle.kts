// SPDX-License-Identifier: AGPL-3.0-only
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "news.openwire.notifications"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
