# Synheart Behavior


> **Source-available.** This repository is open for reading, auditing, and
> filing issues. We do **not** accept pull requests — see
> [CONTRIBUTING.md](CONTRIBUTING.md) for the rationale and how to contribute
> via issues. Security reports go through [SECURITY.md](SECURITY.md).
> On-device behavioral signal inference from digital interactions for Android applications

[![CI](https://github.com/synheart-ai/synheart-behavior-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/synheart-ai/synheart-behavior-kotlin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ai.synheart/synheart-behavior.svg)](https://central.sonatype.com/artifact/ai.synheart/synheart-behavior)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/platform-Android%20API%2021%2B-lightgrey.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0%2B-blue.svg)](https://kotlinlang.org)

A privacy-preserving mobile SDK that collects digital behavioral signals from smartphones. The SDK transforms low-level digital interaction events into structured numerical representations of behavior across event and session. By modeling interaction timing, intensity, fragmentation, and interruption patterns without collecting content or personal data, the SDK provides stable, interpretable metrics to represent digital behavior.

These behavioral signals power downstream systems such as:

- Focus and distraction inference
- Digital wellness analytics
- Cognitive load and fatigue estimation
- Multimodal human state modeling (HSI)

## 🚀 Features

- **Privacy-First**: No text, content, or personally identifiable information (PII) collected—only timing-based signals
- **Real-Time Streaming**: Event streams for scroll, tap, swipe, notification, call, and typing interactions via callback or `Flow`
- **Session Tracking**: Built-in session management with comprehensive summaries
- **On-Demand Metrics**: Calculate behavioral metrics for custom time ranges within sessions
- **Motion State Inference (optional)**: On-device activity recognition (LAYING / MOVING / SITTING / STANDING) via ONNX Runtime when `enableMotionLite` is on. The SDK is the *collector*, not the classifier — runtime is bundled but the model is small and offline.
- **Android Integration**: View attachment helpers for `View`, `RecyclerView`, and root content views
- **Minimal Permissions**: No permissions required for basic functionality (scroll, tap, swipe). Optional permissions for notification and call tracking.
- **Platform Support**: Android API 21+ (Android 5.0+)

## 📦 Installation

### Gradle

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.synheart:synheart-behavior:0.4.2")
}
```

Or with Groovy DSL:

```gradle
dependencies {
    implementation 'ai.synheart:synheart-behavior:0.4.2'
}
```

### Maven

```xml
<dependency>
    <groupId>ai.synheart</groupId>
    <artifactId>synheart-behavior</artifactId>
    <version>0.4.2</version>
</dependency>
```

### Platform Setup

This SDK is **self-contained** and does **not** require bundling any native `.so` libraries.

Motion inference uses ONNX Runtime via the `onnxruntime-android` Gradle dependency, pulled transitively when you add the SDK.

For optional features (notifications and calls), see the [Permissions](#-permissions) section below.

## 🎯 Quick Start

Here's a complete example to get you started:

```kotlin
import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import ai.synheart.behavior.BehaviorSession
import ai.synheart.behavior.SynheartBehavior
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var behavior: SynheartBehavior
        private set

    override fun onCreate() {
        super.onCreate()

        val config = BehaviorConfig(
            enableInputSignals = true,
            enableAttentionSignals = true,
            enableMotionLite = false,
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

        // Listen to real-time events via Flow
        lifecycleScope.launch {
            behavior.onEvent.collect { event ->
                println("Event: ${event.eventType} at ${event.timestamp}")
                println("Metrics: ${event.metrics}")
            }
        }

        // Attach to the root view to capture gestures
        behavior.attachToView(findViewById(android.R.id.content))

        startSession()
    }

    private fun startSession() {
        try {
            currentSession = behavior.startSession()
            println("Session started: ${currentSession?.sessionId}")
        } catch (e: Exception) {
            println("Failed to start session: $e")
        }
    }

    private fun endSession() {
        currentSession?.let { session ->
            try {
                val summary = behavior.endSession(session.sessionId)
                println("Session ended: ${summary.durationMs}ms")
                println("Total events: ${summary.activitySummary.totalEvents}")
                println("Focus hint: ${summary.behavioralMetrics.focusHint}")
                currentSession = null
            } catch (e: Exception) {
                println("Failed to end session: $e")
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
2. **Attach to Views** - Use `attachToView()` to enable gesture tracking
3. **Listen to Events** - Use `Flow` (`onEvent.collect`) or `setEventHandler { … }` for real-time behavioral signals
4. **Track Sessions** - Start and end sessions to get behavioral summaries
5. **Clean Up** - Call `dispose()` when done to free resources

## 📡 Real-Time Event Tracking

The SDK streams behavioral events in real-time as they occur. This is the primary way to track user behavior.

### Using Flow (recommended)

```kotlin
lifecycleScope.launch {
    behavior.onEvent.collect { event ->
        println("Event: ${event.eventType} at ${event.timestamp}")

        when (event.eventType) {
            BehaviorEventType.SCROLL -> {
                val velocity = event.metrics["velocity"] as? Double
                println("Scroll velocity: $velocity px/s")
            }
            BehaviorEventType.TAP -> {
                val duration = event.metrics["tap_duration_ms"] as? Int
                val longPress = event.metrics["long_press"] as? Boolean
                println("Tap duration: $duration ms, long press: $longPress")
            }
            BehaviorEventType.SWIPE -> {
                val direction = event.metrics["direction"] as? String
                val velocity = event.metrics["velocity"] as? Double
                println("Swipe direction: $direction, velocity: $velocity px/s")
            }
            else -> { /* handle other event types */ }
        }
    }
}
```

### Using a Callback Handler

```kotlin
behavior.setEventHandler { event ->
    println("Event: ${event.eventType}")
    println("Metrics: ${event.metrics}")
}
```

## 📊 Event Types

`BehaviorEventType` has **eight canonical values**:
`SCROLL, TAP, SWIPE, APP_SWITCH, NOTIFICATION, CALL, TYPING,
CLIPBOARD`.

- **SCROLL**: Velocity, acceleration, direction, direction reversals
- **TAP**: Duration, long-press detection
- **SWIPE**: Direction, distance, velocity, acceleration
- **APP_SWITCH**: Foreground/background transitions, used for task-switch metrics
- **NOTIFICATION**: Received, opened, ignored (requires permission)
- **CALL**: Answered, ignored, dismissed (requires permission)
- **TYPING**: Speed, cadence, gap ratio, backspace count (no content)
- **CLIPBOARD**: Copy / paste / cut event counts (no content)

> This package is the **collector**. Higher-level behavioral metrics
> (focus hint, distraction score, burstiness, etc.) are computed by
> the Synheart Runtime when these events are fed into Synheart Core.

Each event includes:

- `eventId`: Unique identifier
- `sessionId`: Associated session ID
- `timestamp`: ISO 8601 timestamp
- `eventType`: Type of event (scroll, tap, swipe, etc.)
- `metrics`: Event-specific metrics (velocity, duration, etc.)

## 🔐 Permissions

**Note**: Basic functionality (scroll, tap, swipe) requires **no permissions**. The following permissions are optional and only needed for notification and call tracking.

No content-level information is ever collected or stored. For notifications, the SDK does not record notification text, sender identity, application source, or semantic meaning. For phone calls, the SDK does not record audio, voice data, call content, or call participants.

Instead, the SDK records only event-level metadata, such as:

- the occurrence of a notification or call,
- the timestamp of the event,
- and the user's interaction outcome (e.g., opened, dismissed, ignored).

### Notification Permission

Required for tracking notification interactions (received, opened, ignored). Requires enabling **Notification Access** in Android system settings.

```kotlin
// Check if the system-level notification listener is enabled
val isEnabled = behavior.checkNotificationListenerEnabled()

if (!isEnabled) {
    // Open system settings so the user can grant access
    behavior.requestNotificationListenerAccess()
}

// On Android 13+, also check the runtime POST_NOTIFICATIONS permission
val hasPermission = behavior.checkNotificationPermission()
```

### Call Permission

Required for tracking call interactions (answered and ignored). Requires the `READ_PHONE_STATE` runtime permission.

```kotlin
val hasPermission = behavior.checkCallPermission()

if (!hasPermission) {
    // Request via Activity.requestPermissions() or ActivityResultContracts
    // After permission is granted:
    behavior.reinitializePhoneStateListener()
}
```

## 🔧 Configuration

### Initial Configuration

Configure the SDK during creation:

```kotlin
val config = BehaviorConfig(
    // Enable/disable signal types
    enableInputSignals = true,        // Scroll, tap, swipe gestures
    enableAttentionSignals = true,    // App switching, idle gaps, session stability
    enableMotionLite = true,          // On-device motion classification (ONNX)
    emitRawMotionSamples = false,     // Forward raw 50 Hz accel batches downstream

    // Session configuration
    sessionIdPrefix = "MYAPP",        // Custom session ID prefix (default: "SESS")

    // User/device identifiers (optional)
    userId = "user_123",              // Optional: custom user identifier
    deviceId = "device_456",          // Optional: custom device identifier

    // SDK configuration
    behaviorVersion = "1.0.0",        // SDK version identifier for HSI payloads
    consentBehavior = true,           // Consent flag for behavior tracking

    // Advanced settings
    eventBatchSize = 10,              // Events per batch (default: 10)
    maxIdleGapSeconds = 10.0,         // Max idle time before task drop (default: 10.0)
)

val behavior = SynheartBehavior.create(context, config)
behavior.initialize()
```

### Update Configuration at Runtime

You can update the configuration after initialization:

```kotlin
// Disable motion tracking to save battery
behavior.updateConfig(
    BehaviorConfig(
        enableInputSignals = true,
        enableAttentionSignals = true,
        enableMotionLite = false,  // Disabled
    )
)
```

**Note**: Configuration can only be updated when no session is active.

## 📈 Session Management

### Starting a Session

```kotlin
// Start with auto-generated session ID
val session = behavior.startSession()

// Or provide a custom session ID
val session = behavior.startSession("MYAPP-${System.currentTimeMillis()}")
```

**Note**: Sessions are automatically ended after roughly one minute if the app stays in the background, so they do not run indefinitely when users switch away.

### Ending a Session

When a session ends, you receive a comprehensive summary:

```kotlin
val summary = behavior.endSession(session.sessionId)

// Session metadata
println("Session ID: ${summary.sessionId}")
println("Started: ${summary.startAt}")
println("Ended: ${summary.endAt}")
println("Duration: ${summary.durationMs}ms")

// Behavioral metrics
println("Interaction Intensity: ${summary.behavioralMetrics.interactionIntensity}")
println("Distraction Score: ${summary.behavioralMetrics.behavioralDistractionScore}")
println("Focus Hint: ${summary.behavioralMetrics.focusHint}")
println("Deep Focus Blocks: ${summary.behavioralMetrics.deepFocusBlocks.size}")

// Activity summary
println("Total Events: ${summary.activitySummary.totalEvents}")
println("App Switches: ${summary.activitySummary.appSwitchCount}")

// Notification summary
println("Notifications: ${summary.notificationSummary.notificationCount}")
println("Ignore Rate: ${summary.notificationSummary.notificationIgnoreRate}")

// Typing session summary (when typing was detected)
summary.typingSessionSummary?.let { typing ->
    println("Typing Sessions: ${typing.typingSessionCount}")
    println("Average Speed: ${typing.averageTypingSpeed} taps/sec")
    println("Cadence Stability: ${typing.typingCadenceStability}")
    println("Deep Typing Blocks: ${typing.deepTypingBlocks}")
    println("Clipboard Activity Rate: ${typing.clipboardActivityRate}")
    println("Correction Rate: ${typing.correctionRate}")
}

// Motion state (when enableMotionLite is on and data was collected)
summary.motionState?.let { motion ->
    println("Motion State: ${motion.majorState}")
    println("Confidence: ${motion.confidence}")
}
```

### On-Demand Metrics Calculation

Calculate behavioral metrics for a custom time range within a session:

```kotlin
val metrics = behavior.calculateMetricsForTimeRange(
    startTimestampSeconds = 1767688063,      // Unix timestamp in seconds
    endTimestampSeconds = 1767688130,         // Unix timestamp in seconds
    sessionId = "SESS-1767688063415",         // Optional: defaults to current
)

val activity = metrics["activity_summary"] as Map<*, *>
println("Total events: ${activity["total_events"]}")
println("App switches: ${activity["app_switch_count"]}")

val behavioralMetrics = metrics["behavioral_metrics"] as Map<*, *>
println("Interaction intensity: ${behavioralMetrics["interaction_intensity"]}")
println("Distraction score: ${behavioralMetrics["behavioral_distraction_score"]}")

(metrics["motion_state"] as? Map<*, *>)?.let { motion ->
    println("Motion state: ${motion["major_state"]}")
    println("Confidence: ${motion["confidence"]}")
}
```

**Note**: The time range must be within the session's start and end times. The SDK validates this automatically and will throw an error if the range is out of bounds.

### Current Statistics

Get real-time statistics without ending a session:

```kotlin
val stats = behavior.getCurrentStats()
println("Scroll velocity: ${stats.scrollVelocity}")
println("Tap rate: ${stats.tapRate}")
println("App switches per minute: ${stats.appSwitchesPerMinute}")
println("Stability index: ${stats.stabilityIndex}")
```

### Session Status

```kotlin
if (behavior.isInitialized) {
    val currentSessionId = behavior.currentSessionId
    if (currentSessionId != null) {
        println("Active session: $currentSessionId")
    }
}
```

### Core Behavioral Metrics

Session-level outputs include:

- `interactionIntensity`: Overall interaction rate and engagement
- `behavioralDistractionScore`: Behavioral proxy for distraction (0-1)
- `focusHint`: Behavioral proxy for focus quality (0-1)
- `deepFocusBlocks`: Periods of sustained, uninterrupted engagement
- `taskSwitchRate`: Frequency of app switching
- `idleTimeRatio`: Proportion of idle time vs active interaction
- `fragmentedIdleRatio`: Ratio of fragmented vs continuous idle periods
- `burstiness`: Temporal clustering of interaction events
- `notificationLoad`: Notification pressure and response patterns
- `scrollJitterRate`: Scroll pattern irregularity

Typing session summary (when available) also includes:

- `correctionRate`: Proportion of correction actions (backspace/delete) relative to typing taps and corrections.
- `clipboardActivityRate`: Proportion of clipboard actions (copy, paste, cut) relative to typing taps and clipboard actions.

All metrics are bounded, normalized, and numerically stable.

## ⚙️ Additional Features

### View Attachment

To track gestures, attach the SDK to specific views or to the root content view:

```kotlin
// Attach to a single view
behavior.attachToView(myScrollView)

// Attach to a RecyclerView
behavior.attachToView(myRecyclerView)

// Attach to an Activity's root view
behavior.attachToView(findViewById(android.R.id.content))
```

The SDK automatically tracks gestures on attached views. Text-field interactions surface as tap events; full typing events arrive when the SDK detects an in-progress typing session.

### Custom Event Sending

You can manually send events to the SDK. Only the predefined event types are supported (scroll, tap, swipe, notification, call, typing, clipboard, app_switch):

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
    deepTyping = true,
)

behavior.sendEvent(event)
```

### Cleanup

Always dispose of the SDK when done to free resources:

```kotlin
override fun onDestroy() {
    behavior.dispose()
    super.onDestroy()
}
```

## 🔒 Privacy & Compliance

The Synheart SDK is designed around privacy-by-design and data minimization principles. It captures only the minimum interaction metadata required to model digital behavior, without accessing personal, semantic, or content-level information.

### Hard Guarantees

✅ **No PII**: The SDK does not collect names, contacts, account identifiers, message content, or any user-identifying data. All signals are timing-based and structural.

✅ **No content capture**: The SDK does not collect notification text/titles/sender identity, call audio/voice data/participants, or application UI content/screen data.

✅ **No keystroke logging**: Text input is never recorded. Interactions with text fields are captured only as abstract tap events (timing and duration only), without any character-level data.

✅ **No audio or visual recording**: The SDK does not access the screen buffer, screenshots, camera, microphone, or any form of visual/audio capture.

✅ **Permission-scoped tracking only**: Behavioral data is collected exclusively from applications that explicitly receive user permission. The SDK does not monitor, infer, or aggregate behavior across the entire device or across unpermitted applications.

✅ **No tracking across unconsented apps**: The SDK only tracks behavior within the app that integrates it and has received user consent.

✅ **Event-level metadata only**: Collected data is limited to event type (tap, scroll, swipe, notification, call), timestamp, and non-semantic physical metrics (duration, velocity). No semantic interpretation is performed at the data collection stage.

### Connectivity & System Access

✅ **No internet connectivity required**: The SDK functions fully offline and does not require an active internet connection to perform behavioral capture or inference.

✅ **Network availability state only**: The SDK may record a binary system-level indicator of whether network connectivity is present at a given time. This signal does not include network traffic, destinations, IPs, or content, does not trigger any data transmission, and is used solely as contextual metadata.

✅ **No Bluetooth or external connectivity required**: The SDK does not depend on Bluetooth, NFC, or communication with external devices.

✅ **No background network communication**: Behavioral computation and aggregation occur locally without initiating network requests. Any optional data transmission is explicitly controlled, consent-gated, and configurable.

### Processing & Storage

✅ **On-device computation by default**: Behavioral features and metrics are computed locally on the device, minimizing data exposure.

✅ **Ephemeral data handling**: Raw interaction events are processed in-memory and are not persisted in long-term storage unless explicitly configured for research or debugging purposes.

✅ **No third-party data sharing**: The SDK does not share raw or derived behavioral data with advertisers, analytics providers, or external third parties.

### Regulatory alignment

The SDK is designed around the principles of data minimization,
purpose limitation, user consent, and transparency. See the
[privacy audit](https://docs.synheart.ai/privacy/behavior) for the
detailed static-review notes. This is a self-assessment, not a
third-party certification — legal sufficiency for your specific
deployment depends on how you wire consent in your host app.

The SDK does not track users across apps, does not collect device
identifiers by default, and does not share data with ad-network brokers.

## 📱 Platform Support

- ✅ **Android**: Kotlin, API 21+ (Android 5.0+)
- ✅ **Build**: Gradle 8.0+, Kotlin 2.0+, JDK 17

## ⚡ Performance

The SDK is designed for continuous background operation with minimal
resource impact: events are processed on coroutine-backed background
threads, and there is no persistent storage layer to flush. Specific
CPU / memory / battery numbers are deployment-dependent — earlier docs
published fixed targets, but those were design goals, not measured
runtime numbers. Profile your integration with Android Studio's
profiler for ground truth on your device class.

## 🏗️ Architecture

```text
┌──────────────────────────────────────────────────────┐
│                  Your Android App                     │
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │           SynheartBehavior SDK                   │  │
│  │                                                  │  │
│  │  BehaviorConfig ──► SynheartBehavior.create()    │  │
│  │                           │                      │  │
│  │       ┌───────────────────┼───────────────┐      │  │
│  │       ▼                   ▼               ▼      │  │
│  │  GestureCollector  AttentionSignal  MotionSignal  │  │
│  │  (scroll, tap,     Collector        Collector     │  │
│  │   swipe, typing)   (app switch,     (raw 50 Hz   │  │
│  │       │             idle, notif)     accel, ONNX) │  │
│  │       │                   │               │      │  │
│  │       └─────────┬─────────┘───────────────┘      │  │
│  │                 ▼                                 │  │
│  │       Flow<BehaviorEvent>                         │  │
│  │                 │                                 │  │
│  │                 ▼                                 │  │
│  │       BehaviorSession ──► BehaviorSessionSummary  │  │
│  │       (events, stats,    (metrics, typing,        │  │
│  │        windowing)         motion state)            │  │
│  └─────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
         │
         ▼ (passed to synheart-core for HSI ingestion)
```

Signals flow: **Collectors → Flow\<BehaviorEvent\> → BehaviorSession → Summary**.
The SDK never generates HSI directly — it collects and normalizes behavioral signals.

## 🧪 Testing

```bash
./gradlew test
./gradlew lint
./gradlew assembleRelease
```

Tests are in `src/test/` covering models (config, event, session, stats, motion) and internal collectors (input, attention, session tracker).

## 📋 Requirements

- **Android SDK**: API 21+ (Android 5.0+)
- **Kotlin**: 2.0+
- **Gradle**: 8.0+
- **JDK**: 17

## 🔍 Troubleshooting

### SDK Not Initializing

**Problem**: `behavior.initialize()` throws an exception.

**Solutions**:

- Use the `Application` context (`context.applicationContext`) when calling `SynheartBehavior.create(...)`.
- Ensure `compileSdk` is 34+ and `minSdk` is 21+ in your `build.gradle`.
- Check that ONNX Runtime resolves at build time (no offline-mode dependency lockouts).

### No Events Being Collected

**Problem**: `onEvent` Flow / handler is not emitting events.

**Solutions**:

- Make sure you've called `attachToView(...)` on at least one view (the root content view is a safe default).
- Verify a session is started with `startSession()`.
- Check that `enableInputSignals` or `enableAttentionSignals` is `true` in your config.
- For notifications/calls, ensure the corresponding permissions are granted.

### Permission Requests Not Working

**Problem**: Permission requests don't show dialogs or open settings.

**Solutions**:

- **Notification access** is enabled in system settings, not via runtime permission. Use `requestNotificationListenerAccess()` to open the system page.
- For `READ_PHONE_STATE`, request via `ActivityResultContracts.RequestPermission` and call `reinitializePhoneStateListener()` afterward.
- Test on a real device — emulators sometimes silently drop permission UIs.

### Session End Fails

**Problem**: `behavior.endSession(...)` throws an exception or times out.

**Solutions**:

- Ensure the session was actually started and that you pass the correct `sessionId`.
- Check that the SDK is still initialized (`behavior.isInitialized`).
- Background sessions auto-end after about a minute — calling `endSession` on an already-ended session will throw.

### Build Errors

```bash
./gradlew clean
./gradlew --stop
./gradlew build
```

If ONNX Runtime fails to resolve, double-check `mavenCentral()` is in your repositories and that you have network access during the first build.

## 🧪 Example App

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

## 📚 API Reference

### SynheartBehavior

```kotlin
class SynheartBehavior {
    companion object {
        fun create(context: Context, config: BehaviorConfig = BehaviorConfig()): SynheartBehavior
    }

    // Lifecycle
    fun initialize()
    fun dispose()
    val isInitialized: Boolean

    // Sessions
    fun startSession(sessionId: String? = null): BehaviorSession
    fun endSession(sessionId: String): BehaviorSessionSummary
    fun getCurrentStats(): BehaviorStats
    val currentSessionId: String?

    // Events
    val onEvent: Flow<BehaviorEvent>
    fun setEventHandler(handler: (BehaviorEvent) -> Unit)
    fun sendEvent(event: BehaviorEvent)

    // On-demand metrics
    fun calculateMetricsForTimeRange(
        startTimestampSeconds: Int,
        endTimestampSeconds: Int,
        sessionId: String? = null,
    ): Map<String, Any?>

    // Permissions
    fun checkNotificationListenerEnabled(): Boolean
    fun requestNotificationListenerAccess()
    fun checkNotificationPermission(): Boolean
    fun checkCallPermission(): Boolean
    fun reinitializePhoneStateListener()

    // Configuration
    fun updateConfig(newConfig: BehaviorConfig)

    // View tracking
    fun attachToView(view: View)
}
```

### Key Types

| Type | Description |
|---|---|
| `BehaviorConfig` | SDK configuration (signals, batching, consent, motion) |
| `BehaviorEvent` | Single behavioral event with type and payload |
| `BehaviorSession` | Active session handle returned by `startSession()` |
| `BehaviorSessionSummary` | Aggregated metrics (activity, behavioral, typing, motion, notification) |
| `BehaviorStats` | Real-time metrics snapshot (velocity, cadence, tap rate) |
| `BehaviorEventType` | Enum: `SCROLL`, `TAP`, `SWIPE`, `NOTIFICATION`, `CALL`, `TYPING`, `CLIPBOARD`, `APP_SWITCH` |
| `MotionState` | Motion classification result (`majorState`, `confidence`, `mlModel`) |
| `TypingSessionSummary` | Typing metrics (cadence, burstiness, clipboard/correction rates) |

For full API docs, see the [Maven Central package page](https://central.sonatype.com/artifact/ai.synheart/synheart-behavior).

## Contributing

This is a source-available repository. Issues and feature requests are
welcome; pull requests are not accepted at this time. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the rationale and the supported
contribution path.

## 📄 License

Apache 2.0 License - see [LICENSE](LICENSE) file for details.

## 🔗 Links

- 📦 [Maven Central package](https://central.sonatype.com/artifact/ai.synheart/synheart-behavior)
- 🔗 [GitHub repository](https://github.com/synheart-ai/synheart-behavior-kotlin)
- 🔗 [Parent specification repository](https://github.com/synheart-ai/synheart-behavior)
- 📖 [Example App Guide](example/GUIDE.md)
- 🔒 [Privacy audit](https://docs.synheart.ai/privacy/behavior)

## 🔗 Related Projects

| Repository | Description |
|---|---|
| [synheart-behavior](https://github.com/synheart-ai/synheart-behavior) | Specification & docs (Source of Truth) |
| [synheart-behavior-flutter](https://github.com/synheart-ai/synheart-behavior-flutter) | Flutter/Dart SDK |
| [synheart-behavior-swift](https://github.com/synheart-ai/synheart-behavior-swift) | iOS/Swift SDK |
| [synheart-behavior-chrome](https://github.com/synheart-ai/synheart-behavior-chrome) | Chrome extension |

## Patent Pending Notice

This project is provided under an open-source license. Certain underlying systems, methods, and architectures described or implemented herein may be covered by one or more pending patent applications.

Nothing in this repository grants any license, express or implied, to any patents or patent applications, except as provided by the applicable open-source license.

## Not a Medical Device

This SDK is intended for wellness and research use only. It is not a medical device, is not intended to diagnose, treat, cure, or prevent any disease or condition, and has not been evaluated by the FDA or any other regulatory body.
