# Synheart Flux Integration

This document explains how to integrate synheart-flux (Rust library) with SynheartBehavior for HSI-compliant behavioral metrics computation.

## Overview

The SynheartBehavior SDK **requires** synheart-flux for computing behavioral metrics. All behavioral and typing metric calculations are performed by the Rust library, ensuring:
- HSI compliance
- Cross-platform consistency (same Rust code on iOS and Android)
- Baseline support across sessions
- Deterministic, reproducible results

The SDK uses synheart-flux for computing:
- Distraction score and focus hint
- Burstiness (Barabási formula)
- Task switch rate (exponential saturation)
- Notification load (exponential saturation)
- Scroll jitter rate
- Deep focus blocks
- Interaction intensity
- Typing session metrics (typing session count, average keystrokes, typing speed, cadence, etc.)
- **Clipboard activity rate** and **correction rate** (from typing session copy/paste/cut and backspace counts)
- All other HSI-compliant metrics

The SDK does **not** perform any of these calculations locally; it sends event data to Flux and reads the results from the HSI payload (including `meta.clipboard_activity_rate` and `meta.correction_rate`).

**Note**: synheart-flux is **required**. If the library is not available, session ending will fail with an error. Use **synheart-flux v0.3.0 or later** for full typing summary support (including clipboard and correction rates).

## Benefits

- **HSI Compliance**: Metrics computed using synheart-flux are fully HSI-compliant
- **Cross-Platform Consistency**: Same Rust code runs on iOS, Android, and other platforms
- **Baseline Support**: Rolling baselines across 20 sessions
- **Deterministic Output**: Reproducible results for research

## Installation

### Option A: Prebuilt libraries (recommended)

1. Download the synheart-flux Android libraries from the [v0.3.0 release](https://github.com/synheart-ai/synheart-flux/releases/tag/v0.3.0) (or [releases page](https://github.com/synheart-ai/synheart-flux/releases)):
   - `synheart-flux-android-jniLibs.tar.gz` (or equivalent for your version)
2. Extract and place the `.so` files in this SDK's `src/main/jniLibs/`:

```
src/main/jniLibs/
├── arm64-v8a/
│   └── libsynheart_flux.so
├── armeabi-v7a/
│   └── libsynheart_flux.so
├── x86/
│   └── libsynheart_flux.so
└── x86_64/
    └── libsynheart_flux.so
```

**Note**: The JNI bridge library (`libflux_jni_bridge.so`) is built by this SDK's CMake configuration. You only need to provide `libsynheart_flux.so`.

### Option B: Build from a cloned synheart-flux repo

If you have synheart-flux cloned (e.g. in your workspace next to this repo):

```bash
cd /path/to/synheart-flux

# Build Android libraries (set ANDROID_NDK_HOME if needed)
bash scripts/build-android.sh dist/android

# Or for jniLibs layout:
# ANDROID_NDK_HOME=/path/to/ndk bash scripts/build-android.sh dist/android/jniLibs

# Copy into this SDK
cp -r dist/android/arm64-v8a/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/arm64-v8a/
cp -r dist/android/armeabi-v7a/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/armeabi-v7a/
cp -r dist/android/x86/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/x86/
cp -r dist/android/x86_64/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/x86_64/
```

Adjust paths to match your workspace layout.

### Verify Integration

```kotlin
import ai.synheart.behavior.SynheartBehavior
import ai.synheart.behavior.FluxBridge

val sdk = SynheartBehavior.create(applicationContext)
sdk.initialize()

println("synheart-flux available: ${sdk.isFluxAvailable}")
// or directly: FluxBridge.isAvailable()
```

## Usage

### Basic Usage (with HSI output)

```kotlin
import ai.synheart.behavior.SynheartBehavior
import ai.synheart.behavior.BehaviorConfig

val config = BehaviorConfig(
    enableInputSignals = true,
    enableAttentionSignals = true
)

val sdk = SynheartBehavior.create(applicationContext, config)
sdk.initialize()

// Start a session
val session = sdk.startSession()

// ... user interacts with app ...

// End session - uses synheart-flux exclusively
val summary = sdk.endSession(session.sessionId)
// All metrics in summary.behavioralMetrics are computed by Flux
println("Distraction score: ${summary.behavioralMetrics.behavioralDistractionScore}")
println("Focus hint: ${summary.behavioralMetrics.focusHint}")
println("Burstiness: ${summary.behavioralMetrics.burstiness}")
println("Task switch rate: ${summary.behavioralMetrics.taskSwitchRate}")

// Typing summary is from Flux (including clipboard/correction rates)
summary.typingSessionSummary?.let { typing ->
    println("Typing sessions: ${typing.typingSessionCount}")
    println("Clipboard activity rate: ${typing.clipboardActivityRate}")
    println("Correction rate: ${typing.correctionRate}")
}
```

### Using FluxBehaviorProcessor Directly

For more control, use FluxBehaviorProcessor directly:

```kotlin
import ai.synheart.behavior.FluxBridge
import ai.synheart.behavior.FluxBehaviorProcessor

// Check availability (should always be true if libraries are installed)
if (!FluxBridge.isAvailable()) {
    throw IllegalStateException("synheart-flux is required but not available")
}

// Create a stateful processor with baselines
val processor = FluxBehaviorProcessor(baselineWindowSessions = 20)

// Load previous baselines (if any)
val savedBaselines = sharedPrefs.getString("behavior_baselines", null)
if (savedBaselines != null) {
    processor.loadBaselines(savedBaselines)
}

// Process a session
val sessionJson = """
{
    "session_id": "sess-123",
    "device_id": "device-456",
    "timezone": "America/New_York",
    "start_time": "2024-01-15T14:00:00Z",
    "end_time": "2024-01-15T14:30:00Z",
    "events": [
        {"timestamp": "2024-01-15T14:01:00Z", "event_type": "scroll", "scroll": {"velocity": 150.5, "direction": "down"}},
        {"timestamp": "2024-01-15T14:02:00Z", "event_type": "tap", "tap": {"tap_duration_ms": 120}}
    ]
}
"""

val hsiJson = processor.process(sessionJson)
val payload = parseHsiJson(hsiJson)
if (payload != null) {
    println("HSI computed successfully")
}

// Save baselines for next session
val baselines = processor.saveBaselines()
sharedPrefs.edit().putString("behavior_baselines", baselines).apply()

// Clean up
processor.dispose()
```

### Stateless Processing

For one-shot processing without baselines:

```kotlin
val hsiJson = FluxBridge.behaviorToHsi(sessionJson)
if (hsiJson != null) {
    val payload = parseHsiJson(hsiJson)
    // Use HSI metrics
}
```

## Building from Source

See **Option B** under Installation above. From a cloned synheart-flux repo, run:

```bash
cd /path/to/synheart-flux
ANDROID_NDK_HOME=/path/to/ndk bash scripts/build-android.sh dist/android/jniLibs

# Copy into this SDK (adjust paths to your workspace)
cp dist/android/jniLibs/arm64-v8a/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/arm64-v8a/
cp dist/android/jniLibs/armeabi-v7a/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/armeabi-v7a/
cp dist/android/jniLibs/x86_64/libsynheart_flux.so /path/to/synheart-behavior-kotlin/src/main/jniLibs/x86_64/
```

The build script produces arm64-v8a, armeabi-v7a, and x86_64. Add x86 separately if needed.

## Verifying Integration

Check Logcat for these messages:

**Success:**

```
FluxBridge: Successfully loaded libsynheart_flux.so
FluxBridge: Successfully loaded libflux_jni_bridge.so
FluxBridge: JNI methods available
SessionTracker: Successfully computed metrics using synheart-flux - 15ms
```

**Error (SDK will fail):**

```
FluxBridge: Failed to load native libraries: ...
IllegalStateException: Failed to load synheart-flux native libraries...
```

## Typing Summary from Flux

The session summary's `typingSessionSummary` (e.g. `BehaviorSessionSummary.typingSessionSummary`) is populated from Flux's HSI **meta** section. Flux aggregates per-typing-session counts (backspace, copy, paste, cut, tap count) and computes:

- **clipboard_activity_rate**: `(copy + paste + cut) / (typing_tap_count + copy + paste + cut)`
- **correction_rate**: `(backspace + delete) / (typing_tap_count + backspace + delete)`

The SDK sends these counts in each typing event payload; Flux returns the aggregated rates in `meta`. No calculations are done in the SDK. Use synheart-flux **v0.3.0 or later** so that `meta` includes these fields.

## HSI Output Format

The HSI payload includes:

```kotlin
data class HsiBehaviorPayload(
    val hsiVersion: String,           // "1.0.0"
    val producer: HsiProducer,        // name, version, instance_id
    val provenance: HsiProvenance,    // source_device_id, timestamps
    val quality: HsiQuality,          // coverage, confidence, flags
    val behaviorWindows: List<HsiBehaviorWindow>
)

data class HsiBehaviorWindow(
    val sessionId: String,
    val startTimeUtc: String,
    val endTimeUtc: String,
    val durationSec: Double,
    val behavior: HsiBehavioralMetrics,
    val baseline: HsiBehaviorBaseline?,
    val eventSummary: HsiEventSummary
)

data class HsiBehavioralMetrics(
    val distractionScore: Double,      // 0.0 to 1.0
    val focusHint: Double,             // 0.0 to 1.0
    val taskSwitchRate: Double,        // 0.0 to 1.0
    val notificationLoad: Double,      // 0.0 to 1.0
    val burstiness: Double,            // 0.0 to 1.0
    val scrollJitterRate: Double,
    val interactionIntensity: Double,
    val deepFocusBlocks: Int,
    val idleRatio: Double,
    val fragmentedIdleRatio: Double
)
```

## Troubleshooting

### Native library not found

Ensure the `.so` files are:

1. In the correct architecture subdirectories under `jniLibs`
2. Named `libsynheart_flux.so` (with the `lib` prefix)
3. Built for the correct ABI (arm64-v8a for most modern devices)

### UnsatisfiedLinkError at runtime

- The library uses JNI for native method binding
- Ensure the JNI method signatures in Rust match the Kotlin declarations
- Check that the app is signed correctly for release builds

### Baseline data not persisting

- Use `processor.saveBaselines()` before the app terminates
- Store the JSON string in SharedPreferences or encrypted storage
- Load with `processor.loadBaselines()` on next launch

### Clipboard activity rate or correction rate always 0

- These values are computed by Flux and returned in the HSI `meta` section. If they stay 0 despite typing with copy/paste/cut or backspace, you are likely using an older `libsynheart_flux.so` that does not populate them.
- Download **synheart-flux v0.3.0 or later** from the [releases page](https://github.com/synheart-ai/synheart-flux/releases) and replace the `.so` files in `src/main/jniLibs/<abi>/`.

## API Reference

### SynheartBehavior

```kotlin
// Check if synheart-flux is available
val isFluxAvailable: Boolean

// End session - uses Flux exclusively (throws exception if Flux unavailable)
fun endSession(sessionId: String): BehaviorSessionSummary

// End session with HSI output (optional, for advanced use cases)
fun endSessionWithHsi(sessionId: String, fluxProcessor: FluxBehaviorProcessor? = null): HsiBehaviorPayload?
```

### FluxBridge

```kotlin
// Singleton access
object FluxBridge {
    // Check availability
    fun isAvailable(): Boolean

    // Stateless processing
    fun behaviorToHsi(sessionJson: String): String?

    // Create processor
    fun createProcessor(baselineWindowSessions: Int = 20): Long
}
```

### FluxBehaviorProcessor

```kotlin
// Create with baseline window
class FluxBehaviorProcessor(baselineWindowSessions: Int = 20) : AutoCloseable

// Process session
fun process(sessionJson: String): String

// Baseline management
fun saveBaselines(): String
fun loadBaselines(baselinesJson: String)

// Clean up (also called by close())
fun dispose()
```
