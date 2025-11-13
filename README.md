# Synheart Behavioral SDK for Android

A lightweight, privacy-preserving Android SDK that collects digital behavioral signals from smartphones. These signals represent biobehavioral markers strongly correlated with cognitive and emotional states, especially focus, stress, engagement, and fatigue.

## Features

- 🎯 **Privacy-First**: No text, content, or PII collected - only timing-based signals
- ⚡ **Lightweight**: <150 KB compiled, <2% CPU usage, <500 KB memory footprint
- 🔄 **Event Streaming**: Real-time event callbacks for behavioral signals
- 📊 **Session Tracking**: Built-in session management with summaries
- 🎨 **Kotlin-First**: Modern Kotlin API with coroutines support

## Installation

### Gradle

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'ai.synheart:behavior:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>ai.synheart</groupId>
    <artifactId>behavior</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Initialization

```kotlin
import ai.synheart.behavior.*

val config = BehaviorConfig(
    enableInputSignals = true,
    enableAttentionSignals = true,
    enableMotionLite = false
)

val behavior = SynheartBehavior.create(context, config)
behavior.initialize()
```

### Event Handling

```kotlin
behavior.setEventHandler { event ->
    Log.d("Behavior", "Event type: ${event.type}")
    Log.d("Behavior", "Payload: ${event.payload}")
    Log.d("Behavior", "Timestamp: ${event.timestamp}")
}
```

### Session Tracking

```kotlin
val sessionId = behavior.startSession()

// ... user interacts with app ...

val summary = behavior.endSession(sessionId)
Log.d("Behavior", "Session duration: ${summary.duration}ms")
Log.d("Behavior", "Total events: ${summary.eventCount}")
```

### Manual Polling

```kotlin
val stats = behavior.getCurrentStats()
Log.d("Behavior", "Current typing cadence: ${stats.typingCadence ?: 0}")
Log.d("Behavior", "Scroll velocity: ${stats.scrollVelocity ?: 0}")
```

## Privacy & Compliance

- ✅ No PII collected
- ✅ No keystroke content
- ✅ No screen capture
- ✅ No app content
- ✅ Fully local processing
- ✅ GDPR/CCPA-ready
- ✅ Android Privacy Sandbox friendly

## Performance

- <2% CPU usage
- <500 KB memory footprint
- <2% battery overhead
- <1 ms processing latency
- Zero background threads

## Requirements

- Android API 21+ (Android 5.0+)
- Kotlin 1.9.0+
- Gradle 8.0+

## License

MIT License

## Author

Israel Goytom

