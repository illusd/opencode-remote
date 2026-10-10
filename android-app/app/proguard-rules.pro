# OpenCode Remote ProGuard Rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.HiltViewModel

# Keep Room entities and DAOs
-keep class com.opencode.remote.data.local.** { *; }

# Keep Serialization
-keep class kotlinx.serialization.** { *; }
-keep class com.opencode.remote.data.model.** { *; }

# Keep Ktor
-keep class io.ktor.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# Keep ViewModels
-keep class com.opencode.remote.ui.viewmodel.** { *; }

# Keep Services
-keep class com.opencode.remote.service.** { *; }

# Keep Receivers
-keep class com.opencode.remote.receiver.** { *; }

# Keep Navigation
-keep class androidx.navigation.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Flow
-keep class kotlinx.coroutines.flow.** { *; }

# Keep Annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Don't obfuscate enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}