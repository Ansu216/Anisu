# Extensions are loaded reflectively from separate APKs at runtime, so keep
# the contract they implement against.
-keep interface com.kernel.anime.extension.api.** { *; }
-keep class com.kernel.anime.core.model.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kernel.anime.**$$serializer { *; }
-keepclassmembers class com.kernel.anime.** { *** Companion; }
-keepclasseswithmembers class com.kernel.anime.** { kotlinx.serialization.KSerializer serializer(...); }
