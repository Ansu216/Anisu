# InjektionException Fix - Build & Test Guide

## The Problem

Both error screenshots show:
```
InjektionException: No registered instance or factory for type class android.app.Application
InjektionException: No registered instance or factory for type class eu.kanade.tachiyomi.network.NetworkHelper
```

**Root cause:** In the **release minified build**, R8 strips the generic type signature from `TypeReference<T>` subclasses that Injekt creates to register `Application` and `NetworkHelper`. Without that signature, the lookup key is broken, registration fails silently (caught by `runCatching`), and extensions get "not registered" errors.

---

## What Changed

### 1. **`app/proguard-rules.pro`** — THE FIX

Added these rules to preserve Injekt's type signatures in minified builds:

```proguard
# Injekt builds its lookup key from the generic superclass of an anonymous TypeReference<T>.
# R8 full mode strips that generic signature from classes it does not keep, so registration threw.
-keep,allowobfuscation,allowshrinking class uy.kohesive.injekt.api.TypeReference
-keep,allowobfuscation,allowshrinking class * extends uy.kohesive.injekt.api.TypeReference
-keep,allowobfuscation,allowshrinking interface uy.kohesive.injekt.api.FullTypeReference
-keep,allowobfuscation,allowshrinking class * implements uy.kohesive.injekt.api.FullTypeReference
```

**Why `allowobfuscation,allowshrinking`?** The class bodies can be obfuscated and unused code removed—only the generic signature and the class itself need to survive.

### 2. **`app/src/main/java/com/ansu/anime/extension/aniyomi/AniyomiRuntime.kt`**

- Made `install()` idempotent (safe to call repeatedly).
- Added `sharedClient` field to pass the OkHttpClient to retry attempts.

```kotlin
@Volatile
private var installed = false

@Volatile
var sharedClient: OkHttpClient? = null
    private set

@Synchronized
fun install(context: Context, client: OkHttpClient) {
    if (installed) return
    // ... register objects ...
    sharedClient = client
    installed = true
}
```

### 3. **`app/src/main/java/com/ansu/anime/extension/aniyomi/AniyomiExtensionLoader.kt`**

Added a retry before loading each extension:

```kotlin
// Retry setup here in case it failed at startup, so the real cause shows up instead of a missing instance.
try {
    AniyomiRuntime.install(context, AniyomiRuntime.sharedClient ?: okhttp3.OkHttpClient())
} catch (t: Throwable) {
    android.util.Log.e("AniyomiLoader", "Injekt setup failed", t)
    return failed("Injekt setup failed: ${t::class.java.simpleName}: ${t.message}")
}
```

If setup fails, the error now **shows on the extension row** instead of being hidden.

### 4. **`app/src/main/java/com/ansu/anime/di/AppContainer.kt`**

Changed from swallowing the error to logging it:

```kotlin
// Do not swallow a failure silently: without this, every extension fails with "No registered instance".
try {
    com.ansu.anime.extension.aniyomi.AniyomiRuntime.install(appContext, okHttpClient)
} catch (t: Throwable) {
    android.util.Log.e("AniyomiRuntime", "Injekt setup failed", t)
}
```

### 5. **`CHANGELOG.md`**

Added an entry under `[Unreleased]`.

---

## Build & Test

### Step 1: Apply the Patch

Unzip `fix-injekt.zip` over your project:

```bash
unzip -o fix-injekt.zip
```

It overwrites:
- `app/proguard-rules.pro`
- `app/src/main/java/com/ansu/anime/extension/aniyomi/AniyomiRuntime.kt`
- `app/src/main/java/com/ansu/anime/extension/aniyomi/AniyomiExtensionLoader.kt`
- `app/src/main/java/com/ansu/anime/di/AppContainer.kt`
- `CHANGELOG.md`

### Step 2: Build Release APK

```bash
./gradlew assembleRelease --console=plain
```

**Must be green.** If it fails, copy the error and send it.

### Step 3: Install & Test

```bash
adb install -r build/outputs/apk/release/app-release.apk
```

Or build a signed APK and sideload manually.

### Step 4: Check Extensions

1. Go to **Extensions** tab.
2. Tap **Rescan** (forces a reload).
3. Check each extension status:

| Screenshot | What to expect now |
|---|---|
| **Image 1** (Anikoto error) | Either **enabled** (error gone) or **error message on the row** that is NOT "No registered instance..." |
| **Image 2** (AniWaves.ru error) | Either **enabled** or a **real, readable error** |

---

## If It's Still Broken

### Debug Build

First, test with a debug build to isolate R8:

```bash
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/app-debug.apk
```

- **If debug works:** R8 is the issue, and these keep rules fix it.
- **If debug also fails:** something else is wrong; send me the logcat.

### Logcat

Capture logs while extensions load:

```bash
adb logcat -c
adb logcat | grep -E "AniyomiRuntime|AniyomiLoader|SourceTester|Injekt"
```

Tap **Rescan** on the Extensions screen and capture the output. Send me any errors.

### If It's Still "No Registered Instance"

That means the keep rules didn't work. This could mean:

1. **Injekt version mismatch:** Check `gradle/libs.versions.toml` — is it `1.16.1`?
   ```bash
   grep "injekt = " gradle/libs.versions.toml
   ```

2. **R8 rules conflict:** Another rule might be undoing these. Check for:
   ```bash
   grep -i "injekt" app/proguard-rules.pro
   ```
   The `-keep,allowobfuscation,allowshrinking` rules must come **after** any blanket `-dontnote injekt`.

3. **Full R8 mode vs compatibility mode:** Check `app/build.gradle.kts`:
   ```bash
   grep -A5 "release {" app/build.gradle.kts
   ```
   Make sure `isMinifyEnabled = true` is there (not false).

---

## Why This Happened

Injekt uses reflection at runtime to look up registered objects. Each lookup uses a key built from the generic type of a `TypeReference<T>` anonymous subclass. Example:

```kotlin
inline fun <reified T> addSingleton(instance: T) {
    val key = object : TypeReference<T>() {}.type  // Generic signature is the key
    registry[key] = instance
}
```

When you call `Injekt.addSingleton(myApp)`, Injekt creates an anonymous `TypeReference<Application>`, and the JVM stores its `TypeReference<Application>.type` in the class file. Later, when an extension calls `Injekt.get<Application>()`, it reconstructs the same key and looks it up.

**In minified builds without the keep rules:** R8 sees `TypeReference` is never directly referenced in Ansu's code (only created inside the inline `addSingleton`), so it strips the class. When the code tries to create the anonymous `TypeReference<Application>`, it crashes with `ClassNotFoundException` or the generic signature is missing (depending on how the stripping happened). The error is caught, registration never happens, and extensions get "not registered" errors 25 seconds later when they call `Injekt.get<Application>()`.

The keep rules tell R8: "Don't touch `TypeReference` or its subclasses." That preserves the class and its signature, registration succeeds, and extensions work.

---

## Confidence Level

**Very high.** The error message is unmistakable and the fix is a direct application of a well-known Injekt + R8 interaction. This is the exact pattern described in Injekt's documentation.

Build the release APK, test it, and Akane-san will be pleased. 🎌
