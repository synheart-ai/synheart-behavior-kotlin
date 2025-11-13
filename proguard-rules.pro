# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep public API
-keep public class ai.synheart.behavior.** { *; }

# Keep data classes
-keep class ai.synheart.behavior.**$$serializer { *; }

