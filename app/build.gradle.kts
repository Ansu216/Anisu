plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// version.txt in the repository root is the single source of truth for the version, the version
// code and the channel (see CLAUDE.md section 7). It carries three value lines and "#" comments:
//
//   line 1 = version    line 2 = version code    line 3 = channel
//
// The channel decides how the build is named: a `nightly` build is `<version>-nightly`, anything
// else is the plain `<version>`. CI overrides all of it on the command line, so both legs of one
// run carry the same name, e.g.
//   ./gradlew assembleRelease -PversionCode=42 -PversionName=1.2.3-nightly
//
// The name carries no timestamp on purpose. A `-YYYY.MM.DD.HHMM` suffix used to be appended to
// tell two builds of the same day apart, which made every hourly build look like a different app
// and put a time where a version belongs. The commit a build was made from (`-PbuildCommit`,
// exposed as BuildConfig.BUILD_COMMIT) is what identifies a build now, and the updater compares
// that instead of a stamp in the name.
val versionValues = rootProject.file("version.txt")
    .takeIf { it.isFile }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") }
    .orEmpty()

fun versionValue(index: Int, name: String): String =
    versionValues.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: error("version.txt must carry three value lines ($name is missing): version, version code, channel")

val baseVersion = versionValue(0, "version")
val baseVersionCode = versionValue(1, "version code").toIntOrNull()
    ?: error("version.txt line 2 (version code) is not an integer: '${versionValues.getOrNull(1)}'")
val baseChannel = versionValue(2, "channel").lowercase()

// The version code is a plain build number (version.txt line 2), bumped by one per release and
// shared by both channels, so several nightlies in a row carry the same code on purpose. It is no
// longer derived from the clock, but it still has to stay above every code already published: the
// old date-coded values (YYYYMMDDHH) reached 2026101200, so the counter starts at 2026101201.
// Android only accepts an APK whose code is not lower than the installed one and reports a lower
// one as "the package appears to be invalid" - an update the user can never install - so keep
// bumping it, never lowering it.

val channel = (project.findProperty("channel") as String?)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    ?: baseChannel
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: baseVersionCode
val ciVersionName = (project.findProperty("versionName") as String?)
    ?: if (channel == "nightly") "$baseVersion-nightly" else baseVersion

// The commit this build was made from, written into BuildConfig.BUILD_COMMIT. CI passes GITHUB_SHA;
// a local build leaves it empty. The updater compares it to decide whether a published nightly is a
// build it does not have yet, because the version name and the version code are the same on every
// nightly by design.
val buildCommit = (project.findProperty("buildCommit") as String?)?.trim().orEmpty()

// Your AniList client id (https://anilist.co/settings/developer). Set it without editing
// this file via gradle.properties / local command line: -PANILIST_CLIENT_ID=12345
val aniListClientId = (project.findProperty("ANILIST_CLIENT_ID") as String?) ?: "YOUR_ANILIST_CLIENT_ID"

// Optional TMDB key (free, https://www.themoviedb.org/settings/api) used only to find ENGLISH title logos.
// Leave it empty and the app shows the plain English title instead of a Japanese logo in English mode.
// Set it via gradle.properties / -PTMDB_API_KEY=... / the TMDB_API_KEY environment variable.
val tmdbApiKey = (project.findProperty("TMDB_API_KEY") as String?) ?: System.getenv("TMDB_API_KEY") ?: ""

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
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "ANILIST_REDIRECT_URI", "\"ansu://anilist-auth\"")
        // The channel from version.txt, so the running build can say which channel it came from.
        buildConfigField("String", "RELEASE_CHANNEL", "\"$channel\"")
        // The commit this build was made from; empty in a local build (see `buildCommit` above).
        buildConfigField("String", "BUILD_COMMIT", "\"$buildCommit\"")
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
            // Sign with every scheme Android understands, not just v2. A v2-only APK is
            // rejected or flagged as untrusted by some installers, OEM package managers
            // and older devices; v1 (JAR) covers them, v2 is the modern baseline and v3
            // adds key-rotation support. The certificate is unchanged either way.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
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
    // JavaScriptEngine for extensions that evaluate site scripts (Aniyomi registers it in Injekt too).
    implementation(libs.quickjs.android)
    // Base of HttpServer, the local proxy some extensions start to serve their streams.
    implementation(libs.nanohttpd)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
}
