# Extensions are loaded reflectively from separate APKs at runtime, so keep
# the contract they implement against.
-keep interface com.ansu.anime.extension.api.** { *; }
-keep class com.ansu.anime.core.model.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ansu.anime.**$$serializer { *; }
-keepclassmembers class com.ansu.anime.** { *** Companion; }
-keepclasseswithmembers class com.ansu.anime.** { kotlinx.serialization.KSerializer serializer(...); }

# Aniyomi/Keiyoushi extensions are separate APKs compiled against these libraries and bundle none
# of them, so everything they can call must survive shrinking and renaming.
# NOTE: plain -keep, NOT -keep,allowoptimization. With allowoptimization R8 sees that no class inside
# Ansu overrides e.g. ParsedAnimeHttpSource.episodeListParse and marks it `final`; the extension (loaded
# later) then fails with "LinkageError: ... overrides final method".
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keep class eu.kanade.tachiyomi.** { public protected *; }
-keep class uy.kohesive.injekt.** { public protected *; }
-keep class rx.** { public protected *; }
-keep class org.jsoup.** { public protected *; }
-keep class okhttp3.** { public protected *; }
-keep class okio.** { public protected *; }
-keep class kotlin.** { public protected *; }
-keep class kotlinx.coroutines.** { public protected *; }
-keep class kotlinx.serialization.** { public protected *; }
-keep class androidx.preference.** { public protected *; }
-keep class app.cash.quickjs.** { *; }
-keep class fi.iki.elonen.** { public protected *; }
-dontwarn fi.iki.elonen.**
-dontwarn rx.**
-dontwarn org.jsoup.**
-dontwarn uy.kohesive.injekt.**

# Injekt builds its lookup key from the generic superclass of an anonymous TypeReference<T> that the
# inline `Injekt.addSingleton(x)` / `Injekt.get<T>()` creates. R8 full mode strips that generic
# signature from classes it does not keep, so registering Application / NetworkHelper threw and every
# extension then failed with "InjektionException: No registered instance or factory for type ...".
-keep,allowobfuscation,allowshrinking class uy.kohesive.injekt.api.TypeReference
-keep,allowobfuscation,allowshrinking class * extends uy.kohesive.injekt.api.TypeReference
-keep,allowobfuscation,allowshrinking interface uy.kohesive.injekt.api.FullTypeReference
-keep,allowobfuscation,allowshrinking class * implements uy.kohesive.injekt.api.FullTypeReference

# OkHttp 5 / Okio reference optional platform classes that are not on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.internal.graal.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.serialization.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn java.lang.Module

# ExoPlayer creates HLS/DASH factories reflectively; without these the release build crashes with
# ClassNotFoundException: androidx.media3.exoplayer.hls.HlsMediaSource$Factory
-keep class androidx.media3.exoplayer.hls.** { *; }
-keep class androidx.media3.exoplayer.dash.** { *; }
-dontwarn androidx.media3.**
