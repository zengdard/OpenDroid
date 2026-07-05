# LiteRT
-keep class com.google.ai.edge.litert.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.raissummit.orchestrator.data.models.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
