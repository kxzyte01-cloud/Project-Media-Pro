# kotlinx.serialization ships its own consumer ProGuard/R8 rules inside the library artifact
# (as documented at https://github.com/Kotlin/kotlinx.serialization#android).
# CI runs `assembleRelease` with `isMinifyEnabled = true` to verify R8 shrinking and serialization rules.

# Keep native JNI methods for offline Open Whisper (whisper.cpp)
-keep class com.projectmediapro.feature.captions.WhisperNativeBridge { *; }

# Keep Kotlin serialization metadata & enum valueOf/values used across saveable dialogs and schema v1 JSON
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keepclassmembers enum com.projectmediapro.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep custom Media3 GL effects
-keep class com.projectmediapro.app.data.export.OpticalFlowGlEffect** { *; }
