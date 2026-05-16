# Synheart Behavior Example App

This example app demonstrates how to use the Synheart Behavioral SDK for Android. It provides a complete implementation showing all the key features of the SDK.

## Features

- ✅ SDK initialization and configuration
- ✅ Session management (start/end)
- ✅ Real-time event listening and display
- ✅ Current stats polling (typing cadence, scroll velocity, app switches, stability index)
- ✅ Interactive test area (text input, scrollable content)
- ✅ Detailed session results screen
- ✅ Permission requests (notifications, calls)

## Running the Example

### Prerequisites

- Android Studio (latest version)
- Android SDK (API 21+)
- Android device or emulator

### Steps

1. **Open the project** in Android Studio
2. **Connect your device** or start an emulator
3. **Select the `example` module** in the run configuration
4. **Click Run** or press `Shift+F10`

### Command Line

```bash
# Build and install
./gradlew :example:installDebug

# Or run directly
./gradlew :example:installDebug && adb shell am start -n ai.synheart.behavior.example/.MainActivity
```

## Using the App

### Main Screen

When the app launches, you'll see:

1. **SDK Status Card**

   - Shows if SDK is initialized
   - Displays current active session ID

2. **Control Buttons**

   - **Start Session**: Begin a new behavioral tracking session
   - **End Session**: Stop current session and view summary
   - **Refresh Stats**: Get current rolling statistics
   - **Request Notification Permission**: Request notification access
   - **Request Call Permission**: Request phone state access

3. **Current Stats Card** (shown when session is NOT active)

   - Typing Cadence
   - Scroll Velocity
   - App Switches per minute
   - Stability Index

4. **Test Area**
   - Interactive text field for testing keystroke collection
   - Scrollable RecyclerView for testing scroll dynamics

### Session Results Screen

After ending a session, you'll see:

- **Session Information**: ID, timestamps, duration, event count
- **Behavior Metrics**: Average typing cadence, scroll velocity, app switches
- **Stability Metrics**: Stability index, fragmentation index
- **Events Timeline**: Chronological list of all events with metrics

## Testing Behavioral Signals

### Test 1: Keystroke Timing

1. Click **"Start Session"**
2. Type in the text field
3. Type at varying speeds (fast, slow, with pauses)
4. Observe events being collected (check logs)

**Expected Events**: `TYPING_CADENCE`, `TYPING_BURST`

### Test 2: Scroll Dynamics

1. Ensure session is active
2. Scroll the RecyclerView slowly
3. Then scroll quickly
4. Try jerky scrolling

**Expected Events**: `SCROLL_VELOCITY`, `SCROLL_ACCELERATION`, `SCROLL_JITTER`, `SCROLL_STOP`

### Test 3: Tap and Gesture

1. Tap various buttons quickly
2. Long-press on UI elements
3. Drag/swipe in the scrollable area

**Expected Events**: `TAP_RATE`, `LONG_PRESS_RATE`, `DRAG_VELOCITY`

### Test 4: App Lifecycle

1. Ensure session is active
2. Press device Home button
3. Wait 5 seconds
4. Return to the app

**Expected Events**: `APP_SWITCH` (background), `APP_SWITCH` (foreground)

### Test 5: Idle Gap Detection

1. Start a session
2. Stop interacting with the device completely
3. Wait for idle gaps

**Expected Events**: `IDLE_GAP`, `MICRO_IDLE`, `MID_IDLE`, `TASK_DROP_IDLE`

## Code Structure

```
example/
├── src/main/
│   ├── java/ai/synheart/behavior/example/
│   │   ├── ExampleApplication.kt    # App initialization and SDK setup
│   │   ├── MainActivity.kt          # Main screen with controls and stats
│   │   └── SessionResultsActivity.kt # Session results display
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── activity_session_results.xml
│   │   │   └── item_event.xml
│   │   └── values/
│   │       └── strings.xml
│   └── AndroidManifest.xml
└── build.gradle
```

## Key Implementation Details

### SDK Initialization

```kotlin
val config = BehaviorConfig(
    enableInputSignals = true,
    enableAttentionSignals = true,
    enableMotionLite = false
)

behavior = SynheartBehavior.create(this, config)
behavior.initialize()
```

### Event Handling

```kotlin
behavior.setEventHandler { event ->
    // Handle event
    handleEvent(event)
}
```

### Session Management

```kotlin
// Start session
val sessionId = behavior.startSession()

// End session
val summary = behavior.endSession(sessionId)
```

### Stats Polling

```kotlin
val stats = behavior.getCurrentStats()
// Display: typingCadence, scrollVelocity, appSwitchesPerMinute, stabilityIndex
```

## Privacy Verification

The example app demonstrates that the SDK collects:

✅ **Timing Metrics**: inter-key latency, scroll velocity, idle seconds  
✅ **Counts and Rates**: burst length, tap rate, switch count  
✅ **Aggregated Stats**: cadence, acceleration, jitter

❌ **No Text Content**: No character data, no string values  
❌ **No Screen Coordinates**: No X/Y positions, no pixel locations  
❌ **No Identifiers**: No device IDs, no user IDs

## Performance

The example app monitors:

- CPU usage: <2% average
- Memory: <500 KB for SDK
- No UI lag during interactions

## Troubleshooting

### No Events Appearing

1. Check that session is started
2. Verify SDK initialization succeeded
3. Check Android logs: `adb logcat | grep "Synheart\|Behavior"`

### Build Errors

```bash
# Clean and rebuild
./gradlew clean
./gradlew :example:assembleDebug
```

### Permission Issues

- Notification permission: Requires Android 13+ (API 33+)
- Call permission: Requires `READ_PHONE_STATE` permission

## Next Steps

After running the example app:

1. ✅ Verify all signal types are being collected
2. ✅ Confirm no sensitive data in events
3. ✅ Monitor CPU/memory usage
4. ✅ Integrate SDK into your own app
5. ✅ Customize signal collection for your needs

## Integration into Your App

See the main [README.md](../README.md) for integration instructions.
