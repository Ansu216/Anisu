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
