# General Android rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimize for size and speed
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Remove Debug Logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Kotlin Serialization
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.json.** { *; }
-keep class * extends kotlinx.serialization.internal.GeneratedSerializer { *; }
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class com.stpplay.android.data.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.stpplay.android.database.** { *; }
-keep interface com.stpplay.android.database.** { *; }

# Hilt / Dagger
-keep class com.google.dagger.** { *; }
-keep class com.google.dagger.hilt.** { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.** { *; }
-keep interface dagger.hilt.** { *; }
-keep @dagger.hilt.EntryPoint class *
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.UnsafeCasts { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Coil Image Loading
-keep class coil.** { *; }
-keep interface coil.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }

# Data Models (Safety for all models)
-keep class com.stpplay.android.data.** { *; }

# Prevent obfuscation of R classes to avoid issues with resource lookups by name (if any)
-keep class **.R$* {
    <fields>;
}

# Keep the Application class
-keep class com.stpplay.android.IPTVApplication { *; }
