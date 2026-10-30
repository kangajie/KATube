# Proguard rules untuk KATube

# NewPipeExtractor: jaga semua kelas publik
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Rhino / NewPipeExtractor missing Java SE desktop classes on Android
-dontwarn java.beans.**
-dontwarn javax.script.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Hilt (Dependency Injection)
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Media3 (ExoPlayer & Session)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# KATube Data Models
-keep class id.katube.data.model.** { *; }
-keep class id.katube.data.local.** { *; }
