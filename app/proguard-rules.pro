# Jellyfin SDK models are serialised with kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class org.jellyfin.sdk.model.**$$serializer { *; }
-keepclassmembers class org.jellyfin.sdk.model.** { *** Companion; }
-keep,includedescriptorclasses class app.blush.tv.**$$serializer { *; }
-keepclassmembers class app.blush.tv.** { *** Companion; }

# FFmpeg decoder is loaded via reflection by Media3
-keep class androidx.media3.decoder.ffmpeg.** { *; }

-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep real class names so error messages and crash reports stay readable.
-dontobfuscate
