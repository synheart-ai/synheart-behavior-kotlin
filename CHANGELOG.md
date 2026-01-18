# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2025-01-09

### Added

- **Typing Event Tracking**: Comprehensive typing session metrics including speed, cadence, burstiness, and deep typing detection
- **Motion State Inference**: ML-based activity recognition (LAYING, MOVING, SITTING, STANDING) using ONNX Runtime
- **On-Demand Metrics Calculation**: Calculate behavioral metrics for custom time ranges within sessions via `calculateMetricsForTimeRange()`
- **Kotlin Flow Support**: Reactive event streaming using Kotlin Flow in addition to callback handlers
- **Session Spacing**: Automatic calculation of time elapsed between session end and next session start
- **Manual Event Sending**: `sendEvent()` method for manually sending behavioral events
- **Session Data Persistence**: Ended sessions are stored to allow on-demand metric calculation for historical data
- **Motion Data Collection**: Raw accelerometer and gyroscope data collection with 561-feature ML extraction
- **BehaviorEditText**: Custom EditText widget for typing event tracking (example app)
- **Automatic Session Ending**: Sessions are automatically ended after 1 minute when the app stays in the background

### Changed

- **Session Management**: Sessions now track typing events separately from tap events
- **Event Types**: Added `TYPING` event type with comprehensive typing metrics
- **Session Summary**: Includes typing session summary and motion state when available
- **Configuration**: Added `userId`, `deviceId`, `behaviorVersion`, and `consentBehavior` fields to `BehaviorConfig`
- **Motion State**: Motion state inference runs automatically when motion data is available

### Fixed

- **Tap Count Inflation**: Fixed internal metrics (idle gaps, stability, fragmentation) being incorrectly counted as tap events
- **Notification Tracking**: Fixed duplicate notification events when notifications were opened or ignored
- **Session Not Found**: Fixed `calculateMetricsForTimeRange()` failing for ended sessions
- **Keyboard Dismissal**: Fixed keyboard not dismissing when tapping outside EditText fields
- **Scroll Detection**: Fixed scroll delta calculation for ScrollView and NestedScrollView
- **Feature Ordering**: Ensured motion features are ordered exactly as specified in `features.txt` (no sorting fallback)

### Platform Support

- **Android**: API 21+ (Android 5.0+)
- **Kotlin**: 1.9.0+
- **Gradle**: 8.0+
- **ONNX Runtime**: 1.18.0 (for motion state inference)

### Notes

- Motion state inference requires `enableMotionLite = true` in configuration
- Typing events are separate from tap events and include detailed timing metrics
- On-demand metrics calculation validates time ranges and shows error dialogs for invalid ranges
- Motion state inference runs locally using ONNX Runtime; no data sent to external servers

## [1.0.0] - Unreleased

### Added

- Initial release of Synheart Behavioral SDK for Android
- Real-time event streaming for scroll, tap, swipe, notification, and call interactions
- Session tracking with comprehensive behavioral summaries
- Privacy-preserving behavioral signal collection (timing-based only, no PII)
- Gesture detection on Android views via `attachToView()`
- App lifecycle tracking (app switches, foreground duration, idle gaps)
- Session stability and fragmentation metrics
- Notification and call event tracking (optional, requires permissions)
- Auto-end session after 1 minute in background (example app)

### Features

- **Input Signals**: Scroll, tap, and swipe gesture tracking
- **Attention Signals**: App switching, idle gaps, session stability
- **Event Types**: SCROLL, TAP, SWIPE, NOTIFICATION, CALL, APP_SWITCH
- **Session Management**: Start/end sessions with detailed summaries
- **Real-Time Statistics**: Get current behavioral stats without ending session
- **Privacy-First**: No text content, keystroke content, or PII collected

### Platform Support

- **Android**: API 21+ (Android 5.0+)
- **Kotlin**: 1.9.0+
- **Gradle**: 8.0+

### Notes

- Basic functionality (scroll, tap, swipe) requires no permissions
- Notification tracking requires Notification Access to be enabled in system settings
- Call tracking requires `READ_PHONE_STATE` permission
- Text field interactions are captured as tap events (typing/keystroke tracking is not implemented)
