plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Version can be overridden from the command line / CI, e.g.
//   ./gradlew assembleRelease -PversionCode=42 -PversionName=1.2.3
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = (project.findProperty("versionName") as String?) ?: "0.1.0"

android {
    namespace = "com.ansu.anime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ansu.anime"
        minSdk = 24
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        // Fill these in from https://anilist.co/settings/developer
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"YOUR_ANILIST_CLIENT_ID\"")
        buildConfigField("String", "ANILIST_REDIRECT_URI", "\"kernel://anilist-auth\"")
    }

    // A keystore is committed at keystore/anisu.jks so that local builds and CI
    // produce identically-signed APKs with no setup. The passwords can be
    // overridden (e.g. via -PANISU_STORE_PASSWORD=... or GitHub secrets) without
    // touching this file.
    signingConfigs {
        create("anisu") {
            storeFile = rootProject.file("keystore/anisu.jks")
            storePassword = (project.findProperty("ANISU_STORE_PASSWORD") as String?) ?: "android"
            keyAlias = (project.findProperty("ANISU_KEY_ALIAS") as String?) ?: "anisu"
            keyPassword = (project.findProperty("ANISU_KEY_PASSWORD") as String?) ?: "android"
        }
    }

    buildTypes {
        // Both variants are signed with the same key, so every APK we publish
        // installs as an upgrade over the previous one.
        debug {
            signingConfig = signingConfigs.getByName("anisu")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("anisu")
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
        // TopAppBar / ModalBottomSheet / HorizontalDivider are still marked
        // experimental in Material3. Opt in once here instead of annotating
        // every composable.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
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

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
}
