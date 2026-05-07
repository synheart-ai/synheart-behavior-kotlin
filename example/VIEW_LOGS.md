# How to View Android Logs

## Using ADB Logcat

### 1. Connect your device
Make sure your Android device is connected via USB and USB debugging is enabled.

### 2. View all logs
```bash
adb logcat
```

### 3. Filter logs by tag (recommended)
Filter for notification-related logs:
```bash
adb logcat -s NotificationListener ExampleApplication MainActivity
```

### 4. Filter for specific app package
```bash
adb logcat | grep "ai.synheart.behavior"
```

### 5. Clear logs and start fresh
```bash
adb logcat -c && adb logcat -s NotificationListener ExampleApplication MainActivity
```

### 6. Save logs to file
```bash
adb logcat -s NotificationListener ExampleApplication MainActivity > notification_logs.txt
```

## Using Android Studio

1. Open Android Studio
2. Connect your device
3. Go to **View → Tool Windows → Logcat**
4. Filter by:
   - **Package Name**: `ai.synheart.behavior.example`
   - **Tag**: `NotificationListener` or `ExampleApplication`
   - **Log Level**: Debug or higher

## What to Look For

When testing notification opens, look for these log messages:

- `"Notification posted: <package>"`
- `"Notification removed: <package>"`
- `"Check #X: foreground=<app>, tracking=<package>, stillTracked=true/false"`
- `"Notification opened: <package> (app switched after XXXms)"`
- `"Notification ignored: <package>"`

## Quick Test Command

```bash
# Clear logs, then watch for notification events
adb logcat -c && adb logcat -s NotificationListener:D ExampleApplication:D MainActivity:D | grep -i "notification\|foreground"
```

