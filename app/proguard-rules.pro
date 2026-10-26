# Rules for the R8 optimizer (release builds).

# Data classes read from Navidrome's JSON.
-keep class com.relli.radio.data.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# The playback service is started by name from the manifest and by Android Auto.
-keep class com.relli.radio.playback.PlaybackService { *; }

# OkHttp optional platform classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
