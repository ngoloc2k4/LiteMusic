# ProGuard rules for LiteMusic (Optimized for size & Media3)

# Kotlin Coroutines & Serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclassmembers class * {
    *** Companion;
    *** serializer(...);
}

# AndroidX Media3 / ExoPlayer
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Android Audio Effects (LoudnessEnhancer, DynamicsProcessing)
-keep class android.media.audiofx.** { *; }
