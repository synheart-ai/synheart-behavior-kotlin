# Consumer ProGuard rules for synheart-behavior consumers.
# Applied automatically when an app using this library runs R8/ProGuard
# on a release build.

# ── Public facade + session
-keep class ai.synheart.behavior.SynheartBehavior { *; }
-keep class ai.synheart.behavior.BehaviorSession { *; }
-keep class ai.synheart.behavior.MotionFeatureExtractor { *; }
-keep class ai.synheart.behavior.SynheartNotificationListenerService { *; }

# ── Public data classes / enums (event + summary surface)
-keep class ai.synheart.behavior.ActivitySummary { *; }
-keep class ai.synheart.behavior.BehavioralMetrics { *; }
-keep class ai.synheart.behavior.BehaviorConfig { *; }
-keep class ai.synheart.behavior.BehaviorEvent { *; }
-keep class ai.synheart.behavior.BehaviorEventType { *; }
-keep class ai.synheart.behavior.BehaviorSessionSummary { *; }
-keep class ai.synheart.behavior.BehaviorStats { *; }
-keep class ai.synheart.behavior.DeepFocusBlock { *; }
-keep class ai.synheart.behavior.DeviceContext { *; }
-keep class ai.synheart.behavior.MotionDataPoint { *; }
-keep class ai.synheart.behavior.MotionState { *; }
-keep class ai.synheart.behavior.NotificationSummary { *; }
-keep class ai.synheart.behavior.SystemState { *; }
-keep class ai.synheart.behavior.TypingMetrics { *; }
-keep class ai.synheart.behavior.TypingSessionSummary { *; }
-keep class ai.synheart.behavior.WindowType { *; }

# ── Serialization & enum machinery
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers enum ai.synheart.behavior.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
