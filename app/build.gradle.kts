plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The version can be overridden from the command line / CI, e.g.
//   ./gradlew assembleRelease -PversionCode=42 -PversionName=1.2.3
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = (project.findProperty("versionName") as String?) ?: "0.1.0"

// Your AniList client id (https://anilist.co/settings/developer). Set it without editing
// this file via gradle.properties / local command line: -PANILIST_CLIENT_ID=12345
val aniListClientId = (project.findProperty("ANILIST_CLIENT_ID") as String?) ?: "YOUR_ANILIST_CLIENT_ID"

android {
    namespace = "com.ansu.anime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ansu.anime"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        // Fill these in from https://anilist.co/settings/developer
        // (or override in a non-committed gradle.properties / local.properties entry).
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"$aniListClientId\"")
        buildConfigField("String", "ANILIST_REDIRECT_URI", "\"ansu://anilist-auth\"")
    }

    // A keystore is committed at keystore/anisu.jks so that local builds and CI
    // produce identically-signed APKs with no setup. The credentials can be
    // overridden (e.g. -PANSU_STORE_PASSWORD=... or GitHub secrets) without
    // touching this file.
    signingConfigs {
        create("ansu") {
            storeFile = rootProject.file("keystore/anisu.jks")
            storePassword = (project.findProperty("ANSU_STORE_PASSWORD") as String?) ?: "android"
            keyAlias = (project.findProperty("ANSU_KEY_ALIAS") as String?) ?: "anisu"
            keyPassword = (project.findProperty("ANSU_KEY_PASSWORD") as String?) ?: "android"
        }
    }

    buildTypes {
        // Both variants are signed with the same key, so every APK we publish
        // installs as an upgrade over the previous one (same applicationId).
        debug {
            signingConfig = signingConfigs.getByName("ansu")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("ansu")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Periodic background update check (the "update available" notification while the app is closed).
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // Extensions call these at runtime and bundle none of them (Aniyomi ships them too).
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    // Aniyomi/Keiyoushi extension runtime (see extension/aniyomi and eu.kanade.tachiyomi).
    implementation(libs.androidx.preference.ktx)
    implementation(libs.rxjava)
    implementation(libs.jsoup)
    implementation(libs.injekt.core)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
}
