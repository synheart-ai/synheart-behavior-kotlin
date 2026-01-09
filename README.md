# Synheart Behavioral SDK for Android

[![Maven Central](https://img.shields.io/maven-central/v/ai.synheart/behavior.svg)](https://search.maven.org/artifact/ai.synheart/behavior)

A privacy-preserving mobile SDK that collects digital behavioral signals from smartphones. These timing-based signals represent biobehavioral markers correlated with cognitive and emotional states, especially focus, stress, engagement, and fatigue.

## Features

- **Privacy-First**: No text, content, or personally identifiable information (PII) collected—only timing-based signals
- **Real-Time Streaming**: Event streams for scroll, tap, swipe, notification, call, and typing interactions
- **Session Tracking**: Built-in session management with comprehensive summaries
- **On-Demand Metrics**: Calculate behavioral metrics for custom time ranges within sessions
- **Motion State Prediction**: Activity recognition (LAYING, MOVING, SITTING, STANDING) using ML model inference
- **Kotlin-First**: Modern Kotlin API with coroutines and Flow support
- **Zero Permissions**: No special permissions required for basic functionality
- **Platform Support**: Android API 21+ (Android 5.0+)

## Installation

### Gradle

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'ai.synheart:behavior:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>ai.synheart</groupId>
    <artifactId>behavior</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Quick Start

Here's a complete example to get you started:

```kotlin
import ai.synheart.behavior.*
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MyApplication : Application() {
    lateinit var behavior: SynheartBehavior
        private set

    override fun onCreate() {
        super.onCreate()

        val config = BehaviorConfig(
            enableInputSignals = true,
            enableAttentionSignals = true,
            enableMotionLite = false
        )

        behavior = SynheartBehavior.create(this, config)
        behavior.initialize()
    }
}

class MainActivity : AppCompatActivity() {
    private val behavior by lazy { (application as MyApplication).behavior }
    private var currentSession: BehaviorSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up event handler (callback approach)
        behavior.setEventHandler { event ->
            android.util.Log.d("Behavior", "Event: ${event.eventType} at ${event.timestamp}")
            android.util.Log.d("Behavior", "Metrics: ${event.metrics}")
        }

        // Or use Flow for reactive event streaming
        lifecycleScope.launch {
            behavior.onEvent.collect { event ->
                android.util.Log.d("Behavior", "Event: ${event.eventType}")
            }
        }

        // Start a session
        startSession()
    }

    private fun startSession() {
        try {
            currentSession = behavior.startSession()
            android.util.Log.d("Behavior", "Session started: ${currentSession?.sessionId}")
        } catch (e: Exception) {
            android.util.Log.e("Behavior", "Failed to start session: $e")
        }
    }

    private fun endSession() {
        currentSession?.let { session ->
            try {
                val summary = behavior.endSession(session.sessionId)
                android.util.Log.d("Behavior", "Session ended: ${summary.durationMs}ms")
                android.util.Log.d("Behavior", "Total events: ${summary.activitySummary.totalEvents}")
                android.util.Log.d("Behavior", "Focus hint: ${summary.behavioralMetrics.focusHint}")
                currentSession = null
            } catch (e: Exception) {
                android.util.Log.e("Behavior", "Failed to end session: $e")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        behavior.dispose()
    }
}
```

### Key Steps

1. **Initialize the SDK** - Create and initialize `SynheartBehavior` in your `Application` class
2. **Attach to Views** - Use `attachToView()` to enable gesture tracking on views
3. **Listen to Events** - Set an event handler or use Flow to receive real-time behavioral signals
4. **Track Sessions** - Start and end sessions to get behavioral summaries
5. **Calculate On-Demand Metrics** - Use `calculateMetricsForTimeRange()` for custom time ranges
6. **Clean Up** - Call `dispose()` when done to free resources

## Real-Time Event Tracking

The SDK streams behavioral events in real-time as they occur. This is the primary way to track user behavior:

### Using Callback Handler

```kotlin
behavior.setEventHandler { event ->
    android.util.Log.d("Behavior", "Event: ${event.eventType} at ${event.timestamp}")
    android.util.Log.d("Behavior", "Metrics: ${event.metrics}")

    // Handle different event types
    when (event.eventType) {
        BehaviorEventType.SCROLL -> {
            val velocity = event.metrics["velocity"] as? Double
            android.util.Log.d("Behavior", "Scroll velocity: $velocity px/s")
        }
        BehaviorEventType.TAP -> {
            val duration = event.metrics["tap_duration_ms"] as? Int
            val longPress = event.metrics["long_press"] as? Boolean
            android.util.Log.d("Behavior", "Tap duration: $duration ms, long press: $longPress")
        }
        BehaviorEventType.SWIPE -> {
            val direction = event.metrics["direction"] as? String
            val velocity = event.metrics["velocity"] as? Double
            android.util.Log.d("Behavior", "Swipe direction: $direction, velocity: $velocity px/s")
        }
        BehaviorEventType.TYPING -> {
            val tapCount = event.metrics["typing_tap_count"] as? Int
            val speed = event.metrics["typing_speed"] as? Double
            val deepTyping = event.metrics["deep_typing"] as? Boolean
            android.util.Log.d("Behavior", "Typing: $tapCount taps, speed: $speed, deep: $deepTyping")
        }
        // ... handle other event types
        else -> {}
    }
}
```

### Using Flow (Reactive Approach)

```kotlin
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope

lifecycleScope.launch {
    behavior.onEvent.collect { event ->
        when (event.eventType) {
            BehaviorEventType.SCROLL -> {
                // Handle scroll event
            }
            BehaviorEventType.TYPING -> {
                // Handle typing event
            }
            // ... handle other event types
            else -> {}
        }
    }
}
```

## Event Types

The SDK collects six types of behavioral events:

- **Scroll**: Velocity, acceleration, direction, direction reversals
- **Tap**: Duration, long-press detection
- **Swipe**: Direction, distance, velocity, acceleration
- **Notification**: Received, opened, ignored (requires permission)
- **Call**: Answered, ignored, dismissed (requires permission)
- **Typing**: Comprehensive typing session metrics including speed, cadence, burstiness, and deep typing detection

Each event includes:

- `eventId`: Unique identifier
- `sessionId`: Associated session ID
- `timestamp`: ISO 8601 timestamp
- `eventType`: Type of event (scroll, tap, swipe, etc.)
- `metrics`: Event-specific metrics (velocity, duration, etc.)

## Permissions

**Note**: Basic functionality (scroll, tap, swipe) requires **no permissions**. The following permissions are optional and only needed for notification and call tracking.

### Notification Permission

Required for tracking notification interactions (received, opened, ignored).

**Android**: Requires enabling Notification Access in system settings

```kotlin
// Check if notification listener is enabled
val isEnabled = behavior.checkNotificationListenerEnabled()

if (!isEnabled) {
    // Open system settings to enable notification access
    behavior.requestNotificationListenerAccess()
}

// Check if notification permission is granted (Android 13+)
val hasPermission = behavior.checkNotificationPermission()
```

### Call Permission

Required for tracking call interactions (answered, ignored, dismissed).

**Android**: Requires `READ_PHONE_STATE` permission

```kotlin
// Check if permission is granted
val hasPermission = behavior.checkCallPermission()

if (!hasPermission) {
    // Request permission using Activity.requestPermissions() or ActivityResultContracts
    // After permission is granted, call:
    behavior.reinitializePhoneStateListener()
}
```

## Configuration

### Initial Configuration

Configure the SDK during creation:

```kotlin
val config = BehaviorConfig(
    // Enable/disable signal types
    enableInputSignals = true,        // Scroll, tap, swipe gestures
    enableAttentionSignals = true,    // App switching, idle gaps, session stability
    enableMotionLite = false,         // Device motion + ML inference (optional, may impact battery)

    // Session configuration
    sessionIdPrefix = "MYAPP",        // Custom session ID prefix (default: "SESS")
    eventBatchSize = 10,              // Events per batch for streaming (default: 10)

    // Advanced settings
    maxIdleGapSeconds = 10.0,         // Max idle time before task drop (default: 10.0)

    // HSI payload fields (optional)
    userId = "anon_43a8cd",           // Anonymous user ID (auto-generated if null)
    deviceId = "synheart_android_14", // Device ID (auto-generated if null)
    behaviorVersion = "0.2.0",       // SDK version for HSI payloads
    consentBehavior = true            // Behavior tracking consent (default: true)
)

val behavior = SynheartBehavior.create(context, config)
behavior.initialize()
```

### Update Configuration at Runtime

You can update the configuration after initialization:

```kotlin
// Disable motion tracking to save battery
behavior.updateConfig(BehaviorConfig(
    enableInputSignals = true,
    enableAttentionSignals = true,
    enableMotionLite = false,  // Disabled
))
```

**Note**: Configuration can only be updated when no session is active.

## Session Management

### Starting a Session

```kotlin
// Start with auto-generated session ID
val session = behavior.startSession()

// Or provide a custom session ID
val session = behavior.startSession("MYAPP-${System.currentTimeMillis()}")
```

**Note**: Sessions are automatically ended after 1 minute if the app stays in the background. This prevents sessions from running indefinitely when users switch away from your app.

### Ending a Session

When a session ends, you receive a comprehensive summary:

```kotlin
val summary = behavior.endSession(session.sessionId)

// Session metadata
android.util.Log.d("Behavior", "Session ID: ${summary.sessionId}")
android.util.Log.d("Behavior", "Started: ${summary.startAt}")
android.util.Log.d("Behavior", "Ended: ${summary.endAt}")
android.util.Log.d("Behavior", "Duration: ${summary.durationMs}ms")

// Behavioral metrics
android.util.Log.d("Behavior", "Focus Hint: ${summary.behavioralMetrics.focusHint}")
android.util.Log.d("Behavior", "Distraction Score: ${summary.behavioralMetrics.behavioralDistractionScore}")
android.util.Log.d("Behavior", "Interaction Intensity: ${summary.behavioralMetrics.interactionIntensity}")
android.util.Log.d("Behavior", "Deep Focus Blocks: ${summary.behavioralMetrics.deepFocusBlocks.size}")

// Activity summary
android.util.Log.d("Behavior", "Total Events: ${summary.activitySummary.totalEvents}")
android.util.Log.d("Behavior", "App Switches: ${summary.activitySummary.appSwitchCount}")

// Notification summary
android.util.Log.d("Behavior", "Notifications: ${summary.notificationSummary.notificationCount}")
android.util.Log.d("Behavior", "Ignore Rate: ${summary.notificationSummary.notificationIgnoreRate}")

// Typing session summary (if typing events occurred)
summary.typingSessionSummary?.let { typing ->
    android.util.Log.d("Behavior", "Typing Sessions: ${typing.typingSessionCount}")
    android.util.Log.d("Behavior", "Average Speed: ${typing.averageTypingSpeed} taps/sec")
    android.util.Log.d("Behavior", "Deep Typing Blocks: ${typing.deepTypingBlocks}")
}

// Motion state (if motion tracking enabled)
summary.motionState?.let { motion ->
    android.util.Log.d("Behavior", "Motion State: ${motion.majorState}")
    android.util.Log.d("Behavior", "Confidence: ${motion.confidence}")
}
```

### On-Demand Metrics Calculation

Calculate behavioral metrics for a custom time range within a session:

```kotlin
// Calculate metrics for a specific time range
val metrics = behavior.calculateMetricsForTimeRange(
    startTimestampSeconds = 1767688063,  // Unix timestamp in seconds
    endTimestampSeconds = 1767688130,     // Unix timestamp in seconds
    sessionId = "SESS-1767688063415"      // Optional: session ID (uses current if not provided)
)

// Access the calculated metrics
android.util.Log.d("Behavior", "Total events: ${(metrics["activity_summary"] as Map<*, *>)["total_events"]}")
android.util.Log.d("Behavior", "App switches: ${(metrics["activity_summary"] as Map<*, *>)["app_switch_count"]}")
val behavioralMetrics = metrics["behavioral_metrics"] as Map<*, *>
android.util.Log.d("Behavior", "Interaction intensity: ${behavioralMetrics["interaction_intensity"]}")
android.util.Log.d("Behavior", "Distraction score: ${behavioralMetrics["behavioral_distraction_score"]}")

// Motion state (if motion data is available)
(metrics["motion_state"] as? Map<*, *>)?.let { motionState ->
    android.util.Log.d("Behavior", "Motion state: ${motionState["major_state"]}")
    android.util.Log.d("Behavior", "Confidence: ${motionState["confidence"]}")
}
```

**Note**: The time range must be within the session's start and end times. The SDK validates this automatically and will throw an error if the range is out of bounds.

### Motion State Inference

When `enableMotionLite` is enabled, the SDK uses machine learning to predict user activity states from motion sensor data. The model classifies activity into four states: **LAYING**, **MOVING**, **SITTING**, and **STANDING**.

Motion state inference is automatically performed:

- When ending a session (if motion data is available)
- When calling `calculateMetricsForTimeRange()` (if motion data is available for that time range)

```kotlin
// Motion state is included in session summaries
val summary = behavior.endSession(session.sessionId)
summary.motionState?.let { motion ->
    android.util.Log.d("Behavior", "Predicted State: ${motion.majorState}")
    android.util.Log.d("Behavior", "State Distribution: ${motion.state}")
    android.util.Log.d("Behavior", "Major State Percentage: ${motion.majorStatePct * 100}%")
    android.util.Log.d("Behavior", "Confidence: ${motion.confidence}")
    android.util.Log.d("Behavior", "ML Model: ${motion.mlModel}")
}

// Motion state is also available in on-demand metrics
val metrics = behavior.calculateMetricsForTimeRange(
    startTimestampSeconds = startSeconds,
    endTimestampSeconds = endSeconds
)
(metrics["motion_state"] as? Map<*, *>)?.let { motionState ->
    android.util.Log.d("Behavior", "Motion State: ${motionState["major_state"]}")
}
```

**Note**: Motion state inference requires:

- `enableMotionLite = true` in configuration
- Motion data collected during the session
- ONNX Runtime library (automatically included as a dependency)

### Current Statistics

Get real-time statistics without ending a session:

```kotlin
val stats = behavior.getCurrentStats()
android.util.Log.d("Behavior", "Scroll velocity: ${stats.scrollVelocity}")
android.util.Log.d("Behavior", "Tap rate: ${stats.tapRate}")
android.util.Log.d("Behavior", "App switches per minute: ${stats.appSwitchesPerMinute}")
```

### Session Status

```kotlin
// Check if SDK is initialized
if (behavior.isInitialized) {
    // Check current active session
    val currentSessionId = behavior.currentSessionId
    if (currentSessionId != null) {
        android.util.Log.d("Behavior", "Active session: $currentSessionId")
    }
}
```

## View Attachment

To track gestures on specific views, attach the SDK to them:

```kotlin
// Attach to a single view
behavior.attachToView(myScrollView)

// Attach to a RecyclerView
behavior.attachToView(myRecyclerView)

// Attach to an Activity's root view
behavior.attachToView(findViewById(android.R.id.content))
```

**Note**: The SDK automatically tracks gestures on attached views. Text field interactions are captured as tap events. Typing events are created separately when typing sessions are detected.

### Manual Event Sending

You can manually send events to the SDK. Only predefined event types are supported (scroll, tap, swipe, notification, call, typing):

```kotlin
val event = BehaviorEvent.typing(
    sessionId = behavior.currentSessionId ?: "current",
    typingTapCount = 50,
    typingSpeed = 5.2,
    meanInterTapIntervalMs = 192.3,
    typingCadenceVariability = 0.15,
    typingCadenceStability = 0.85,
    typingGapCount = 3,
    typingGapRatio = 0.1,
    typingBurstiness = 0.3,
    typingActivityRatio = 0.9,
    typingInteractionIntensity = 0.8,
    durationSeconds = 10,
    startAt = "2023-01-01T10:00:00Z",
    endAt = "2023-01-01T10:00:10Z",
    deepTyping = true
)

behavior.sendEvent(event)
```

## Privacy & Compliance

- ✅ **No PII collected**: Only timing-based signals, no personal information
- ✅ **No text content**: Typing events only contain timing metrics (speed, cadence, etc.), not actual text
- ✅ **No screen capture**: No screenshots or screen recording
- ✅ **Motion data privacy**: Motion state inference runs locally using ONNX Runtime; no data sent to external servers
- ✅ **No app content**: No access to app UI content or data
- ✅ **Fully local processing**: All processing happens on-device
- ✅ **No persistent storage**: Data stored only in memory
- ✅ **No network transmission**: Zero network activity
- ✅ **GDPR/CCPA-ready**: Compliant with privacy regulations

## Platform Support

- ✅ **Android**: API 21+ (Android 5.0+)
- ✅ **Kotlin**: 1.9.0+
- ✅ **Gradle**: 8.0+

## Requirements

- **Android SDK**: API 21+ (Android 5.0+)
- **Kotlin**: 1.9.0+
- **Gradle**: 8.0+

## Troubleshooting

### SDK Not Initializing

**Problem**: `SynheartBehavior.initialize()` throws an exception.

**Solutions**:

- Ensure you're using `Application` context (use `context.applicationContext`)
- Check that native platform code is properly integrated
- Verify Android API level meets requirements (>= 21)

### No Events Being Collected

**Problem**: Event handler is not receiving events.

**Solutions**:

- Ensure you've attached views using `attachToView()`
- Verify a session is started with `startSession()`
- Check that `enableInputSignals` or `enableAttentionSignals` is `true` in config
- For notifications/calls, ensure permissions are granted

### Permission Requests Not Working

**Problem**: Permission requests don't show dialogs or open settings.

**Solutions**:

- **Android**: Notification access requires manual enablement in system settings
- Check platform-specific permission requirements in your app's manifest
- Ensure you're testing on a real device (emulator may have limitations)

### Session End Fails

**Problem**: `behavior.endSession()` throws an exception or times out.

**Solutions**:

- Ensure the session was properly started
- Check that the SDK is still initialized
- Verify the session ID matches the active session

### Build Errors

```bash
./gradlew clean
./gradlew build
```

## Example App

A complete example app demonstrating all SDK features is available in the [`example/`](example/) directory.

To run the example:

```bash
cd example
./gradlew installDebug
```

The example app includes:

- Real-time event visualization
- Session management UI
- Permission handling examples
- Event type handling demonstrations

## API Reference

For detailed API documentation, see the [Maven Central package page](https://search.maven.org/artifact/ai.synheart/behavior).

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Apache 2.0 License - see [LICENSE](LICENSE) file for details.

## Author

Israel Goytom

## Links

- 📦 [Maven Central](https://search.maven.org/artifact/ai.synheart/behavior)
- 🔗 [GitHub repository](https://github.com/synheart-ai/synheart-behavior-kotlin)

## Patent Pending Notice

This project is provided under an open-source license. Certain underlying systems, methods, and architectures described or implemented herein may be covered by one or more pending patent applications.

Nothing in this repository grants any license, express or implied, to any patents or patent applications, except as provided by the applicable open-source license.
