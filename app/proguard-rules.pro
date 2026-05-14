# Keep source file names and line numbers for cleaner crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata and reflection
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# Jetpack Compose — the compiler emits runtime classes that must survive R8.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# Navigation Compose
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
    @javax.inject.Inject <init>(...);
}
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class **_HiltModules** { *; }
-keep class **_HiltComponents** { *; }
-keep class **_GeneratedInjector { *; }
-keep class dagger.hilt.android.internal.** { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.**

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.CoroutineWorker
-dontwarn androidx.work.**

# ML Kit Text Recognition / Barcode
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# MediaPipe GenAI (on-device LLM)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Tesseract for Android (native library + JNI bindings)
-keep class cz.adaptech.tesseract4android.** { *; }
-keep class com.googlecode.tesseract.** { *; }
-dontwarn cz.adaptech.tesseract4android.**
-dontwarn com.googlecode.tesseract.**

# Google Play Billing
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Parcelable CREATOR fields
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# App models serialized across processes (Room entities, nav args, Intent extras).
-keep class med.reminder.com.data.database.** { *; }
-keep class med.reminder.com.data.model.** { *; }
-keep class med.reminder.com.domain.model.** { *; }
