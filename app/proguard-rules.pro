# LiteRT
-keep class com.google.ai.edge.litert.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.opendroid.data.model.** { *; }
-keep class com.opendroid.provider.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
