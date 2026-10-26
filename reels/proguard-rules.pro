# Rules for the R8 optimizer (release builds).

# Data classes read from Jellyfin's JSON.
-keep class com.relli.reels.data.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
