# Proguard / R8 optimization rules for RivaMusic

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.nexusdigital.rivamusic.data.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Playback Service & Components
-keep class com.nexusdigital.rivamusic.playback.** { *; }
-keep class com.nexusdigital.rivamusic.RivaApplication { *; }
-keep class com.nexusdigital.rivamusic.MainActivity { *; }
