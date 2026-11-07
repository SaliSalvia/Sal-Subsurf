# Minification is disabled (isMinifyEnabled = false) for reproducible, debuggable
# release builds. If you enable R8 later, keep these rules:
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keepattributes *Annotation*, InnerClasses, Signature
# kotlinx-serialization
-keepclassmembers class com.salsalvia.subsurf.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.salsalvia.subsurf.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
