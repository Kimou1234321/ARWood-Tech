# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools proguard-defaults.txt

# Keep data classes used in navigation
-keep class com.algerianwood.app.processing.** { *; }
-keep class com.algerianwood.app.data.** { *; }
