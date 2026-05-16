# Example App Guide

## Running and Testing Real Behavioral Signal Collection

This guide shows you how to run the example app and verify that behavioral signals are being collected in real-time on Android.

---

## Prerequisites

### Software Requirements

- Android Studio (Hedgehog 2023.1.1+) or any IDE with the Android Gradle plugin
- Android SDK Platform 34 installed (`compileSdk = 34`)
- JDK 17 (Android Studio bundles a compatible JDK)
- Gradle 8.0+ (the Gradle wrapper at the repo root pins the right version)
- Kotlin 2.0+ (managed by the Gradle plugin)
- An Android emulator or physical device running **Android 5.0+ (API 21+)**

### Installation Check

```bash
# Verify the Gradle wrapper resolves at the repo root
./gradlew --version

# Expected output should show:
# Gradle 8.x
# Kotlin:       2.x
# JVM:          17

# Verify a device is attached
adb devices
```

---

## Running the Example App

The `example/` module is wired into the root `settings.gradle` as `:example`,
so you can build and install it directly from the repo root.

### Option 1: Run from Android Studio

1. Open the `synheart-behavior-kotlin/` folder as a Gradle project.
2. Wait for the initial sync to finish (it downloads the SDK module and ONNX
   Runtime dependency).
3. From the run-config dropdown select **example**.
4. Press **Run** (or `Shift + F10`).

Android Studio installs `ai.synheart.behavior.example.kotlin` and launches
`MainActivity` on the selected device.

### Option 2: Run from the Command Line

#### Step 1: Build and Install the Debug APK

```bash
# From the repo root
./gradlew :example:installDebug
```

#### Step 2: Launch the App

```bash
adb shell am start -n ai.synheart.behavior.example.kotlin/ai.synheart.behavior.example.MainActivity
```

#### Step 3: Observe Logs (Optional)

```bash
# In a separate terminal, monitor SDK and example app logs
adb logcat | grep -E "Synheart|MainActivity|SessionResults"
```

The example app emits structured `Log.d(...)` calls when sessions start, end,
auto-end after backgrounding, and when notification/call permissions change
state.

---

## Using the Example App

### App Interface

When the app launches, you'll see:

1. **SDK Status Card**

   - Shows whether the SDK is initialized (`Initialized` indicator)
   - Displays the current active session ID once a session is running

2. **Control Buttons**

   - **Start Session**: Begin a new behavioral tracking session
   - **End Session**: Stop the current session and open `SessionResultsActivity`
   - **Refresh Stats**: Read the current rolling statistics via
     `behavior.getCurrentStats()`

3. **Permission Buttons**

   - **Request Notification Permission**: Triggers `POST_NOTIFICATIONS` (API 33+)
     and opens **Notification Access** in system settings to bind
     `SynheartNotificationListenerService`
   - **Request Call Permission**: Triggers a runtime `READ_PHONE_STATE` request
     and reinitializes the phone-state listener afterwards

4. **Current Stats Card** (visible when no session is active)

   - Real-time behavioral metrics:
     - Scroll Velocity
     - App Switches per minute
     - Stability Index

5. **Test Area**

   - A `RecyclerView` with 10 items for scroll testing
   - A `BehaviorEditText` typing field for typing-session telemetry
   - A `ScrollView` wrapping the whole screen for top-level scroll dynamics

6. **Session Results Screen**

   - Pressing **End Session** (or letting a backgrounded session auto-end after
     ~60 s) opens `SessionResultsActivity`, which renders the full
     `BehaviorSessionSummary` plus an event timeline

---

## Testing Behavioral Signal Collection

**Important**: The SDK emits **real-time events** (scroll, tap, swipe,
notification, call, typing, clipboard) via `behavior.onEvent` (a Kotlin `Flow`)
and `behavior.setEventHandler { … }`. Aggregated statistics are computed from
these events and surface via `behavior.getCurrentStats()` while a session is
running, and via `BehaviorSessionSummary` when a session ends. The tests below
exercise both individual events and aggregated statistics.

### Test 1: Tap Gesture Signals

**Objective**: Verify tap gesture detection and timing

**Steps**:

1. Tap **"Start Session"** (or wait for the auto-start in `MainActivity`).
2. Tap various buttons and items in the `RecyclerView`.
3. Try both quick taps and long-press gestures on list items.
4. Open `SessionResultsActivity` (via **End Session**) and inspect the events
   timeline.

**Expected Events** (`BehaviorEventType.TAP`):

- Each tap generates an event with duration and long-press detection.

**Expected Event Shape**:

```json
{
  "event_type": "tap",
  "metrics": {
    "tap_duration_ms": 120,
    "long_press": false
  }
}
```

**Note**: Tap rate is computed from tap events and is available via
`behavior.getCurrentStats().tapRate`.

**Privacy Check**: The event contains NO coordinates and NO content — only
timing.

---

### Test 2: Scroll Dynamics Signals

**Objective**: Verify scroll velocity, acceleration, and direction tracking

**Steps**:

1. Ensure a session is active.
2. Scroll the `ScrollView` **slowly**.
3. Then scroll the `RecyclerView` **quickly**.
4. Try **jerky** scrolling (start-stop-start).
5. End the session and check the timeline in `SessionResultsActivity`.

**Expected Events** (`BehaviorEventType.SCROLL`):

- Emitted while scrolling, with velocity, acceleration, direction, and
  direction-reversal metrics.

**Expected Event Shape**:

```json
{
  "event_type": "scroll",
  "metrics": {
    "velocity": 150.5,
    "acceleration": 25.3,
    "direction": "down",
    "direction_reversal": false
  }
}
```

**Note**: Scroll jitter and aggregated scroll statistics are derived from
scroll events and available via `behavior.getCurrentStats()` and
`summary.behavioralMetrics.scrollJitterRate`.

**Privacy Check**: No screen coordinates — only velocity magnitude and
direction.

---

### Test 3: Swipe and Long-Press Signals

**Objective**: Verify swipe gesture detection and long-press handling

**Steps**:

1. Tap items in the `RecyclerView` rapidly (multiple times).
2. **Long-press** on a list item.
3. **Swipe** horizontally and vertically inside the `RecyclerView`.
4. Inspect the events timeline.

**Expected Events**:

- `BehaviorEventType.TAP` events with `tap_duration_ms` and `long_press`
- `BehaviorEventType.SWIPE` events with direction, distance, velocity, and
  acceleration

**Expected Tap Event Shape**:

```json
{
  "event_type": "tap",
  "metrics": {
    "tap_duration_ms": 150,
    "long_press": false
  }
}
```

**Expected Swipe Event Shape**:

```json
{
  "event_type": "swipe",
  "metrics": {
    "direction": "left",
    "distance_px": 250.5,
    "duration_ms": 300,
    "velocity": 835.0,
    "acceleration": 120.5
  }
}
```

**Note**: Swipe gestures are tracked automatically once you call
`behavior.attachToView(...)` on the `RecyclerView` or root view.

**Privacy Check**: No raw coordinates — only timing and aggregate movement
metrics.

---

### Test 4: Typing Session Signals

**Objective**: Verify typing-session detection through the `BehaviorEditText`
field

**Steps**:

1. Tap the typing field on `MainActivity` to open the keyboard.
2. Type a sentence at varying speeds.
3. Use **Backspace** a couple of times.
4. **Copy** and **paste** some text.
5. Tap outside the field (or press **End Session**) — this closes the keyboard
   and ends the typing session, which emits a `TYPING` event with computed
   metrics.

**Expected Events** (`BehaviorEventType.TYPING` and `BehaviorEventType.CLIPBOARD`):

```json
{
  "event_type": "typing",
  "metrics": {
    "typing_tap_count": 32,
    "typing_speed": 4.8,
    "mean_inter_tap_interval_ms": 208.3,
    "typing_cadence_stability": 0.74,
    "typing_gap_count": 1,
    "typing_gap_ratio": 0.03,
    "typing_burstiness": 0.41,
    "typing_activity_ratio": 0.92,
    "typing_interaction_intensity": 0.78,
    "duration_seconds": 6,
    "deep_typing": false,
    "backspace_count": 2,
    "number_of_paste": 1,
    "number_of_copy": 0,
    "number_of_cut": 0
  }
}
```

```json
{
  "event_type": "clipboard",
  "metrics": {
    "action": "paste",
    "context": "textField"
  }
}
```

**Note**: Typing sessions in the example app are computed locally inside
`BehaviorEditText` and forwarded to the SDK via `behavior.sendEvent(event)`.
The SDK aggregates them into `summary.typingSessionSummary` (active typing
ratio, burstiness, deep-typing blocks, correction rate, clipboard activity rate).

**Privacy Check**: No characters, words, or field names are recorded — only
timing and aggregate counts.

---

### Test 5: App Lifecycle Signals

**Objective**: Verify foreground/background detection and auto-end

**Steps**:

1. Make sure a session is active.
2. Press the device **Home** button (or swipe up).
3. Wait at least **5 seconds**, then return to the app.
4. Repeat the cycle, then press **End Session** to view the summary.

**Note**: App lifecycle events are tracked internally by the SDK as
`BehaviorEventType.APP_SWITCH`. The example app filters them out of the on-screen
timeline (see `SessionResultsActivity.displayEvents`), but the counts surface
in the summary's activity card and in `behavior.getCurrentStats()`.

**Expected Stats** (from `behavior.getCurrentStats()`):

```kotlin
val stats = behavior.getCurrentStats()
println("App switches/min: ${stats.appSwitchesPerMinute}")
println("Foreground duration: ${stats.foregroundDuration}s")
```

**Auto-end**: `MainActivity` registers a `LifecycleEventObserver` that starts a
60-second background timer when the app pauses. If the app stays in the
background for 60 seconds, the session auto-ends and `SessionResultsActivity`
is launched the next time the user returns to the foreground.

---

### Test 6: Idle Gap Detection

**Objective**: Verify idle-state tracking

**Steps**:

1. Start a session.
2. **Stop interacting** with the device completely.
3. Wait for:
   - 2 seconds (micro idle)
   - 5 seconds (mid idle)
   - 12 seconds (task-drop idle, equal to `BehaviorConfig.maxIdleGapSeconds`)
4. Tap **Refresh Stats**.

**Note**: Idle gaps are tracked internally and exposed via the rolling stats
and the per-session summary (`summary.behavioralMetrics.idleTimeRatio`,
`summary.behavioralMetrics.fragmentedIdleRatio`).

**Expected Stats**:

```kotlin
val stats = behavior.getCurrentStats()
println("Idle gap seconds: ${stats.idleGapSeconds}")
```

---

### Test 7: Session Stability Metrics

**Objective**: Verify stability and fragmentation calculations

**Steps**:

1. Start a session.
2. Use the app **steadily** for 1–2 minutes.
3. Switch apps 2–3 times.
4. Tap **Refresh Stats** to read live values, or press **End Session** to view
   the full summary.

**Expected Stats** (live):

```kotlin
val stats = behavior.getCurrentStats()
// stats.stabilityIndex      → 0.0 - 1.0, higher = more stable
// stats.fragmentationIndex  → 0.0 - 1.0, higher = more fragmented
```

**Interpretation**:

- **High stability** (>0.8): user is focused, few interruptions.
- **Low stability** (<0.5): user is distracted, many app switches.

The session summary exposes the same intuition through
`summary.behavioralMetrics.focusHint` and
`summary.behavioralMetrics.behavioralDistractionScore`.

---

### Test 8: Session Summary

**Objective**: Verify session summary generation in `SessionResultsActivity`

**Steps**:

1. Start a session.
2. Interact with the app for 1–2 minutes:
   - Scroll the `ScrollView` and the `RecyclerView`
   - Tap a few items
   - Perform swipe gestures
   - Type a short sentence in the `BehaviorEditText`
   - Switch apps once or twice
3. Tap **End Session**.
4. Inspect the summary in `SessionResultsActivity`.

**Expected Summary** (key fields rendered by `SessionResultsActivity`):

```
Session ID:           SESS-1705234567890
Duration:             2.0m
Total Events:         87
App Switches:         2
Interaction Intensity: 0.612
Task Switch Rate:     0.034
Idle Time Ratio:      0.221
Active Time Ratio:    0.779
Burstiness:           0.412
Distraction Score:    0.183
Focus Hint:           0.821
Deep Focus Blocks:    1
```

If `enableMotionLite = true` (the example default) and motion data was
collected during the session, the **Motion State** card will also be visible
with `majorState`, `confidence`, `mlModel`, and the per-window state array.

The typing-session summary card surfaces typing speed, cadence stability,
gap ratio, deep-typing blocks, clipboard activity rate, and correction rate
when at least one typing event was emitted.

---

## Verifying Data Privacy

### What You Should SEE in Events

**Event Types** (from `behavior.onEvent` / `behavior.setEventHandler`):

- `BehaviorEventType.SCROLL` — `velocity`, `acceleration`, `direction`,
  `direction_reversal`
- `BehaviorEventType.TAP` — `tap_duration_ms`, `long_press`
- `BehaviorEventType.SWIPE` — `direction`, `distance_px`, `duration_ms`,
  `velocity`, `acceleration`
- `BehaviorEventType.NOTIFICATION` — `action` (requires permission)
- `BehaviorEventType.CALL` — `action` (requires permission)
- `BehaviorEventType.TYPING` — typing speed, cadence, gap ratio, backspace
  count, paste/copy/cut counts
- `BehaviorEventType.CLIPBOARD` — `action`, `context` (no clipboard contents)
- `BehaviorEventType.APP_SWITCH` — internal app focus transitions

**Aggregated Statistics** (from `behavior.getCurrentStats()`):

- `scrollVelocity` — pixels per second
- `scrollAcceleration` — pixels per second squared
- `scrollJitter`
- `tapRate` — taps per second
- `appSwitchesPerMinute`
- `foregroundDuration` — seconds
- `idleGapSeconds`
- `stabilityIndex`
- `fragmentationIndex`

### What You Should NOT SEE

**Text Content**:

- No character data
- No string values
- No field names

**Screen Coordinates**:

- No X/Y positions
- No pixel locations
- No UI element IDs

**Identifiers**:

- No device IDs (unless you explicitly set `BehaviorConfig.deviceId`)
- No user IDs (unless you explicitly set `BehaviorConfig.userId`)
- No advertising IDs

**System Information**:

- No other app names
- No package identifiers
- No file paths

**Privacy Verification**: If you see any of the above forbidden items, please
file an issue on GitHub.

---

## Performance Verification

### Monitor App Performance

While using the example app:

```bash
# CPU usage
adb shell top -n 1 | grep ai.synheart.behavior.example.kotlin

# Memory usage
adb shell dumpsys meminfo ai.synheart.behavior.example.kotlin | grep -A 10 "App Summary"
```

You can also use Android Studio's built-in **Profiler** for ground-truth
CPU, memory, and energy numbers — values vary with config (which signals are
enabled, how many events your handler emits per second) and the device.

**What to watch for**:

- No lag in UI interactions while the SDK is collecting.
- CPU and memory deltas stay within whatever budget your host app has
  allocated for telemetry.
- No spikes when `enableMotionLite = true` — motion inference runs on a
  short ONNX model batched at 50 Hz.

---

## Troubleshooting

### Problem: No Events Appearing

**Possible Causes**:

1. Session not started.
2. SDK initialization failed in `ExampleApplication.onCreate`.
3. No view was attached to the SDK.

**Solution**:

```bash
# Check logs for SDK lifecycle messages
adb logcat | grep -E "Synheart|MainActivity"

# Look for:
# - "Session ended (autoEnded: ...). Events count: ..."
# - "Notification listener is enabled and connected."
# - No exceptions during behavior.initialize() or behavior.startSession()
```

Verify in code that you actually attached the SDK to the views you want to
track (the example does this in `MainActivity.setupRecyclerView()` and
`MainActivity.setupInputListeners()`):

```kotlin
behavior.attachToView(testRecyclerView)
behavior.attachToView(mainScrollView)
```

### Problem: Build Errors

```bash
# Stop running daemons and clean
./gradlew --stop
./gradlew clean

# Rebuild
./gradlew :example:assembleDebug
```

If ONNX Runtime fails to resolve, double-check that `mavenCentral()` is in
your repositories and that you have network access for the first build —
the dependency is pulled transitively via `:` (the SDK module), which the
example consumes via `implementation project(':')`.

### Problem: Notification or Call Events Not Firing

`SynheartNotificationListenerService` is bound by the system, not by code. The
example registers it in `AndroidManifest.xml`:

```xml
<service
    android:name="ai.synheart.behavior.SynheartNotificationListenerService"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

To get notifications:

1. Tap **Request Notification Permission** in the example app.
2. The app opens **Settings → Notification access**; toggle the example
   app on.
3. On Android 13+, also grant the runtime `POST_NOTIFICATIONS` permission.
4. The example calls `behavior.hotRestartCollectors()` so the listener gets
   picked up without restarting the app.

For calls:

1. Tap **Request Call Permission**.
2. Grant `READ_PHONE_STATE` in the runtime dialog.
3. The example calls `behavior.reinitializePhoneStateListener()` automatically.

### Problem: Session End Throws

**Solutions**:

- Make sure the session was actually started and that you pass the correct
  `sessionId` to `behavior.endSession(...)`.
- Check that the SDK is still initialized (`behavior.isInitialized`).
- Background sessions auto-end after about 60 seconds — calling `endSession`
  on an already-ended session will throw.

---

## Expected Output Examples

### Console Log (Successful Run)

```
D/ExampleApplication: Synheart Behavior SDK Initialized
D/MainActivity: Session started: SESS-1705234567890

D/Synheart: Event: tap
D/Synheart:   Metrics: {tap_duration_ms=120, long_press=false}

D/Synheart: Event: scroll
D/Synheart:   Metrics: {velocity=150.5, acceleration=25.3, direction=down, direction_reversal=false}

D/Synheart: Event: swipe
D/Synheart:   Metrics: {direction=left, distance_px=250.5, duration_ms=300, velocity=835.0, acceleration=120.5}

D/MainActivity: Session ended (autoEnded: false). Events count: 87
D/MainActivity: Summary total events: 87
```

### Event Stream (Real-time)

```json
[
  {
    "event_id": "evt_1705234567890",
    "session_id": "SESS-1705234567890",
    "timestamp": "2025-01-15T10:15:23.456Z",
    "event_type": "tap",
    "metrics": {
      "tap_duration_ms": 120,
      "long_press": false
    }
  },
  {
    "event_id": "evt_1705234568100",
    "session_id": "SESS-1705234567890",
    "timestamp": "2025-01-15T10:15:25.100Z",
    "event_type": "scroll",
    "metrics": {
      "velocity": 150.5,
      "acceleration": 25.3,
      "direction": "down",
      "direction_reversal": false
    }
  },
  {
    "event_id": "evt_1705234570000",
    "session_id": "SESS-1705234567890",
    "timestamp": "2025-01-15T10:15:40.000Z",
    "event_type": "swipe",
    "metrics": {
      "direction": "left",
      "distance_px": 250.5,
      "duration_ms": 300,
      "velocity": 835.0,
      "acceleration": 120.5
    }
  }
]
```

---

## Next Steps

After successfully running the example app:

1. **Verify all signal types**: Make sure scroll, tap, swipe, typing,
   notification, and call events appear in the timeline (with the
   corresponding permissions granted).
2. **Privacy check**: Confirm no sensitive data shows up in events.
3. **Performance check**: Monitor CPU/memory with Android Studio Profiler.
4. **Integrate**: Add the SDK to your own app.
5. **Customize**: Configure `BehaviorConfig` for your needs (motion on/off,
   custom session prefix, batch size, etc.).

---

## Integration into Your App

Once you've verified the example app, wire the SDK into your own app the same
way `ExampleApplication` and `MainActivity` do.

### Application

```kotlin
import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.SynheartBehavior
import android.app.Application

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
```

Register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
    ...
</application>
```

### Activity

```kotlin
import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import ai.synheart.behavior.BehaviorSession
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val behavior by lazy { (application as MyApplication).behavior }
    private var session: BehaviorSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Attach gesture tracking to the root view (covers all child views)
        behavior.attachToView(findViewById(android.R.id.content))

        // Listen to real-time events via Flow
        lifecycleScope.launch {
            behavior.onEvent.collect { event ->
                handleEvent(event)
            }
        }

        // Start a session
        session = behavior.startSession()
    }

    private fun handleEvent(event: BehaviorEvent) {
        when (event.eventType) {
            BehaviorEventType.SCROLL -> {
                val velocity = event.metrics["velocity"] as? Double
                // forward to your analytics pipeline
            }
            BehaviorEventType.TAP -> {
                val durationMs = event.metrics["tap_duration_ms"] as? Int
                // ...
            }
            BehaviorEventType.SWIPE -> {
                val direction = event.metrics["direction"] as? String
                // ...
            }
            else -> { /* handle other event types as needed */ }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.let { runCatching { behavior.endSession(it.sessionId) } }
        // The Application owns the SDK instance, so we don't dispose here.
    }
}
```

### Cleanup

When your `Application` is being torn down (or the SDK is no longer needed),
call `dispose()` to release resources:

```kotlin
override fun onTerminate() {
    behavior.dispose()
    super.onTerminate()
}
```

---

## Support

If you encounter issues:

1. Check the [README.md](../README.md) for setup, configuration, and full API
   reference.
2. Read the [privacy audit](https://docs.synheart.ai/privacy/behavior) for
   privacy questions.
3. File an issue on
   [GitHub](https://github.com/synheart-ai/synheart-behavior-kotlin/issues).

Happy testing.
