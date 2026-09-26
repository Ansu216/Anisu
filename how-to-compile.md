# How to Compile the Anisu APK

This guide explains, step by step, how to build the Anisu Android app from source.
It is written for a beginner: if you have never opened a computer before, follow it
in order. No prior Android knowledge is required.

---

## What you need

You need a computer running **Windows 10/11**, **macOS**, or **Linux**, plus ~4 GB of free disk space.

| Item | Version this project expects | Why it matters |
|---|---|---|
| Java Development Kit (JDK) | 21 | AGP 8.7 and Kotlin 2.1 require Java 21. |
| Android Studio | Ladybug (2024.3.1) or newer | The IDE the project was made for. |
| Command line | PowerShell (Windows), Terminal (macOS/Linux) | You will type a few commands to build the APK. |

> The project already ships a Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`
> pointing at `gradle-8.9-bin.zip`). So you do **not** need to download Gradle or the
> Android SDK separately if you have Android Studio. The SDK is discovered from the
> `local.properties` file and the standard `ANDROID_HOME` environment variable.

---

## Step 1 — Install Android Studio

1. Download Android Studio from https://developer.android.com/studio
2. Run the installer and accept the default options. Android Studio is a normal
   desktop application; the installer will place it in `C:\Program Files\Android\Android Studio` on Windows.
3. During first launch, Android Studio asks you to install the **Android SDK** and
   command-line tools. Accept the default SDK path, which is:
   - Windows: `C:\Users\<YourUser>\AppData\Local\Android\Sdk`
   - macOS: `~/Library/Android/sdk`
   - Linux: `~/Android/Sdk`
4. If you already have Android Studio, make sure the SDK is installed:
   - Open **Settings** (Windows) or **Android Studio → Settings** (macOS/Linux)
   - Go to **Appearance & Behavior → System Settings → Android SDK**
   - Verify the SDK platform, build tools, and platform-tools are installed.

> If you prefer using the command line instead of the IDE, you can still build
> with `gradlew` (see Step 5). But Android Studio is the recommended way to open,
> edit, and debug the project.

---

## Step 2 — Clone the project

Open a terminal and navigate to the folder where you want to save the project:

```powershell
# Windows (PowerShell)
cd C:\Users\<YourUser>\Documents
git clone https://github.com/<your-username>/Anisu.git
cd Anisu
```

```bash
# macOS / Linux (Terminal)
cd ~/Documents
git clone https://github.com/<your-username>/Anisu.git
cd Anisu
```

> Replace `<your-username>` with your actual GitHub username, or use a different
> repository URL if the project was shared with you another way.

---

## Step 3 — Make sure the Java 21 JDK is visible to Android Studio

The project targets Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`) and
uses Kotlin 2.1, so the IDE must be able to find a Java 21 JDK.

### Windows (Android Studio)

1. Open **Settings → Appearance & Behavior → System Settings → SDK Location**.
2. Note the **Android SDK location** (usually `C:\Users\<YourUser>\AppData\Local\Android\Sdk`).
3. Still in **Settings**, go to **Build, Execution, Deployment → Build Tools → JDK**.
4. Click **Add JDK**.
5. Select the **Eclipse Adoptium JDK 21** (or any OpenJDK 21) you installed.
6. Click **OK**.

### macOS / Linux (Android Studio)

1. Open **Android Studio → Settings → Build, Execution, Deployment → Build Tools → JDK**.
2. Ensure the path points to your Java 21 JDK (for example, `/opt/openjdk-21` or
   `~/Library/Java/JavaVirtualMachines/<jdk21>/Contents/Home` on macOS).

### Verify from the command line

Open a **new** terminal window and run:

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
java -version
```

```bash
# macOS / Linux
export JAVA_HOME="/opt/openjdk-21"
java -version
```

You should see a line starting with `openjdk version "21"`.

> If `java -version` prints `1.8`, `11`, or `17`, Android Studio may still build, but the
> recommended JDK 21 is required for the Gradle wrapper and the AGP 8.7 toolchain.

---

## Step 4 — Configure the Android SDK path

The project reads the Android SDK location from `local.properties` (a file you create
locally, **not** committed to Git). Create it in the project root:

### Windows (PowerShell)

```powershell
Set-Content -Path "C:\Users\<YourUser>\Documents\Anisu\local.properties" -Value @"
sdk.dir=C:\Users\<YourUser>\AppData\Local\Android\Sdk
"@
```

Replace `<YourUser>` with your Windows username.

### macOS / Linux

```bash
echo 'sdk.dir=/Users/<YourUser>/Library/Android/sdk' > /path/to/Anisu/local.properties
```

> If you chose a different SDK location, change the path accordingly. The `.gitignore`
> file commits the directory, so a `local.properties` created like this will not be
> accidentally committed.

---

## Step 5 — Open the project in Android Studio

1. Open Android Studio and choose **Open**.
2. Select the `Anisu` folder you cloned in Step 2.
3. Android Studio will start the **Gradle sync**. It will:
   - Download the Gradle wrapper (if not already cached).
   - Resolve all dependencies from Google Maven and Maven Central.
   - Generate the `buildConfig` fields, the Room database schema, and the KSP compiler output.

> **Note:** A Gradle wrapper jar is not checked into this download (see the README).
> Android Studio offers to download it automatically the first time you open the project.
> Simply accept the prompt and let it finish. If you ever need to generate the wrapper
> manually, run: `gradle wrapper` from the project root.

### First run may take a while

Gradle needs to download:
- The Gradle distribution (8.9)
- All Android SDK platforms / build tools
- Every library declared in `gradle/libs.versions.toml`

Be patient. On a normal connection this is usually done within a few minutes.

---

## Step 6 — Adjust the `local.properties` (recommended)

Open `local.properties` at the project root and make sure it contains the correct
SDK path. Add these lines at the end if they are not already there:

```properties
# SDK location
sdk.dir=C:\Users\<YourUser>\AppData\Local\Android\Sdk

# Gradle user home (where the wrapper and caches are stored)
gradle.user.home=C:\Users\<YourUser>\.gradle
```

This file overrides the environment variables and lets the build find everything
it needs without additional configuration.

---

## Step 7 — Build the debug APK

There are two ways to build.

### Via Android Studio (recommended)

1. Open the **Build** menu.
2. Select **Build Bundle(s) / APK(s)**.
3. Choose **Build APK(s)**.

Android Studio will produce an APK like
`app/build/outputs/apk/debug/app-debug.apk`. Install it on your device with:

```powershell
# Windows
& "C:\Users\<YourUser>\AppData\Local\Android\Sdk\platform-tools\adb" install "C:\Users\<YourUser>\AppData\Local\Android\Sdk\app\build\outputs\apk\debug\app-debug.apk"
```

```bash
# macOS / Linux
~/Android/Sdk/platform-tools/adb install ~/Anisu/app/build/outputs/apk/debug/app-debug.apk
```

> The device must have **Developer options** and **USB debugging** enabled, and
> you must have accepted the RSA fingerprint prompt when connecting the USB cable.

### Via the command line (gradlew)

If you prefer not to use the IDE, you can build from the terminal:

```powershell
# Windows
.\gradlew assembleDebug
```

```bash
# macOS / Linux
./gradlew assembleDebug
```

On Windows, if you get a `Permission denied` error, run:

```powershell
chmod +x gradlew
./gradlew assembleDebug
```

### Via Android Studio with a JDK 21 (final check)

After the Gradle sync, run your build. If the compile fails with a Java version
error, Android Studio is not using JDK 21: go back to **Step 3** and set the
**JDK** in the **Build, Execution, Deployment → Build Tools → JDK** page.

---

## Step 8 — Sign and build a release APK

Release build.

The project already ships a configured **release** build type: `isMinifyEnabled = true`,
`proguard-rules.pro` is wired in, and the packaging rule excludes `META-INF/{AL2.0,LGPL2.1}`. So the same
command builds a signed or unsigned **release** APK depending on whether you provide a keystore:

```powershell
./gradlew assembleRelease
```

```bash
./gradlew assembleRelease
```

The output is:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

To sign it you need a **keystore** `JKS` or `PKCS12`. It must contain a single key with a
password and a store password. Two options:

1. **Generate a keystore** (dev machine only, not for production distribution):

   ```powershell
   keytool -genkey -v -keystore my-release-key.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000
   ```

   You will be asked for a key password and a keystore password. Answer them and copy the file
   into the project root.

2. **Use a keystore you already have** (for example the same you use for other apps).

Once the store is ready, tell the build where it is and what passwords to use. Create or edit
`gradle.properties` in the project root and add (replace the paths with your real values):

```properties
# Release keystore
RELEASE_STORE_FILE=my-release-key.jks
RELEASE_KEY_ALIAS=mykey
RELEASE_KEY_PASSWORD=your-key-password
RELEASE_STORE_PASSWORD=your-store-password
```

Then build the release APK as usual. Android Studio will sign it automatically; the terminal
build will do the same if the `RELEASE_*` properties are defined in `gradle.properties`.

> The `debug` build type stays `isMinifyEnabled = false`, so you can always build a debug APK
> as a fallback: `./gradlew assembleDebug`.

> The `release` build also **obfuscates** the code with R8/ProGuard. The `proguard-rules.pro`
> keeps the reflection-based extension loader and the kotlinx.serialization serializers working.
> If you see obfuscation errors at runtime (not at build time), add the missing class names to
> that file.

---

## Step 9 — Install the debug APK on a device or emulator

### Using a physical device

1. Enable **Developer options** on your Android phone:
   - Go to **Settings → About phone** and tap **Build number** seven times.
2. Open **Settings → Developer options** and enable **USB debugging**.
3. Connect your phone with a USB cable and accept the "Allow USB debugging" prompt.
4. Verify the connection in the terminal:

```powershell
.\gradlew installDebug
```

The terminal will show `INSTALL SUCCESS`.

### Using an emulator (Android Studio)

1. Open **_device_ → **AVD Manager**.
2. Create a virtual device (a Pixel 6/7 works well for this app).
3. Download a system image and start the emulator.
4. Run the app from Android Studio, or:

```powershell
.\gradlew installDebug
```

---

## Troubleshooting

### "Could not resolve dependency" / "Failed to resolve"

This usually means Android Studio could not reach the network repositories (Google
Maven, Maven Central). Check your internet connection, or add a proxy setting in
`gradle.properties`:

```properties
systemProp.http.proxyHost=proxy.example.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8080
```

### "Could not find a Java 21 JDK" / "toolchain verification failed"

Android Studio is still using JDK 8/11/17. Set JDK 21 in
**Settings → Build, Execution, Deployment → Build Tools → JDK**.

### "AGP requires Java 21" (AGP 8.7)

You need a Java 21 JDK. Android Studio Ladybug ships one inside its own `jbr` folder;
make sure the IDE does not use an older bundled JDK.

### "SDK location not found" (build fails)

Check `local.properties` contains the correct `sdk.dir` path. On Windows this is
usually:

```powershell
sdk.dir=C:\Users\<YourUser>\AppData\Local\Android\Sdk
```

### "Package '...' was compiled with an incompatible version of Kotlin"

Update `gradle/libs.versions.toml` to the latest versions, or tell Android Studio to
sync and accept the suggested updates.

### "Runner app has stopped" (app crashes on launch)

The first build is likely to have issues, as noted in the repository README:
dependency versions, route/route-name collisions, or a missing API key can make the
first build fail or the app crash on launch. The README includes a list of known
rough edges. Resolve them one by one and rebuild.

---

## After the build: what you need to run the app for real

The build alone is not enough to use the app end-to-end. You need to fill in a
client ID from AniList:

1. Open https://anilist.co/settings/developer and register a new OAuth2 client.
2. Set the **redirect URI** to `anisu://anilist-auth`.
3. Copy the **client ID** into `app/src/main/java/com/kernel/anime/anilist/AniListAuthManager.kt`,
   replacing the placeholder in the `companion object`:

   ```kotlin
   companion object {
       const val ANILIST_CLIENT_ID = "YOUR_ANILIST_CLIENT_ID"  // <-- your real ID here
       private const val KEY_TOKEN = "access_token"
   }
   ```

4. Rebuild: `./gradlew assembleDebug`.

> The redirect URI `anisu://anilist-auth` is already declared in
> `AndroidManifest.xml` and hard-coded in `AniListAuthManager`, so you only need
> to paste the client ID.

The versions this project is pinned to (in `gradle/libs.versions.toml`) are:

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.1.0 |
| KSP | 2.1.0-1.0.29 |
| Compose BOM | 2024.12.01 |

Kotlin, KSP and Compose must stay in sync: the KSP version prefix **must** match the
Kotlin version (`2.1.0-…`). If you bump Kotlin, bump KSP to the matching
`<kotlinVersion>-<kspVersion>` release too, or the build stops with
`ksp-… is too old for kotlin-…`.

---

## Verified build result

This project has been built from a clean checkout with the command-line commands above.
Expected artifacts:

| Variant | Output | Size |
|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | ~23 MB (signed) |
| Release | `app/build/outputs/apk/release/app-release.apk` | ~3 MB (signed, minified) |

The built APK reports:

- **App name:** Anisu
- **Package name:** `com.ansu.anime`
- **Minimum Android:** 7.0 (API 24)
- **Target Android:** 15 (API 35)

Both variants are signed, so the release build is `app-release.apk` (not
`app-release-unsigned.apk`).

---

## Signing

The keystore lives at `keystore/anisu.jks` and is committed on purpose, so local
builds and CI produce **identically-signed** APKs with no setup. Both the `debug`
and `release` build types use it.

| Setting | Value |
|---|---|
| Keystore | `keystore/anisu.jks` |
| Alias | `anisu` |
| Store / key password | `android` |

You can override the passwords without editing any file:

```bash
./gradlew assembleRelease \
  -PANISU_STORE_PASSWORD=yourpass \
  -PANISU_KEY_ALIAS=youralias \
  -PANISU_KEY_PASSWORD=yourpass
```

Verifiy a signed APK:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

See [`keystore/README.md`](keystore/README.md) for the security caveats and how to
move the key to GitHub secrets instead.

---

## CI: nightly and release builds

Two GitHub Actions workflows are included under `.github/workflows/`.

### `apk-nightly.yml` — hourly nightly APK

- **Runs:** every hour (`0 * * * *`) and manually
  (**Actions → Anisu Nightly APK → Run workflow**).
- **Does:** builds the signed debug + release APKs with a timestamped version
  (`versionName=YYYY.MM.DD.HHMM`, `versionCode=YYYYMMDDHH`), verifies the
  signatures, then **force-pushes** them to the `apk-nightly` branch. The branch is
  wiped and replaced on every run.
- **Branch contents:**

  | File | Description |
  |---|---|
  | `Anisu-nightly.apk` | Signed release build. |
  | `Anisu-nightly-debug.apk` | Signed debug build. |
  | `nightly.json` | Version / build metadata. |
  | `README.md` | Short note explaining the branch. |

- **Install:** open `https://github.com/<owner>/<repo>/raw/apk-nightly/Anisu-nightly.apk`
  on your phone, or browse the
  [`apk-nightly` branch](https://github.com/Ansu216/Anisu/tree/apk-nightly).

### `release-apk.yml` — automatic release

- **Runs:** when you push a tag like `v1.0.0`, or manually
  (**Actions → Anisu Release APK → Run workflow**, with an optional tag).
- **Does:** builds and signs the APKs, verifies signatures, and creates a GitHub
  Release named after the tag with `Anisu-<version>.apk` and
  `Anisu-<version>-debug.apk` attached.

### One-time repository setting

The nightly workflow pushes a branch, so the workflow token needs write access:
**Settings → Actions → General → Workflow permissions → Read and write permissions**.

---

## Quick reference

| Task | Command |
|---|---|
| Open the project | Android Studio → **Open** → select the project folder |
| Sync Gradle | **File → Sync Project with Gradle Files** |
| Build debug APK | **Build → Build APK(s)** |
| Build release APK | **Build → Build APK(s)** (release variant) |
| Build both from CLI | `.\gradlew assembleDebug assembleRelease` |
| Install on device | `.\gradlew installDebug` (Windows) / `./gradlew installDebug` (macOS/Linux) |
| Clean build | `.\gradlew clean` |
| Verify signing | `apksigner verify --print-certs <apk>` |
| Check project health | `.\gradlew assembleDebug` (recommended as the single verification command) |
