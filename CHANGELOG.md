# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.1] - 2026-02-18

### Changed

- **Packaging**: Removed Flux JNI bridge and any bundled native libraries so the SDK is ready for Maven Central publishing without shipping `.so` artifacts.
- **Docs**: Updated README to remove Flux installation/troubleshooting and added `PUBLISHING.md` with Maven Local + Sonatype publish steps.

### Build

- **Repo hygiene**: Stop tracking generated Gradle outputs and ignore `**/build/` (fixes accidentally committed `example/build` artifacts).

## [0.4.0] - 2026-02-13

### Added

- **TypingSessionSummary**: `clipboardActivityRate` and `correctionRate` (0.0–1.0) from Flux HSI `meta`. The SDK sends per-typing-session counts (backspace, copy, paste, cut) to Flux and reads the aggregated rates; no calculations are done in the SDK.
- **Individual typing sessions**: Session summary now includes `individualTypingSessions` (list of per-session metrics from Flux `typing_metrics`).
- **Documentation**: README and SYNHEART_FLUX_INTEGRATION updated for Flux v0.3.0+ requirement, typing summary from Flux, and troubleshooting when clipboard/correction rates are 0 (outdated `libsynheart_flux.so`).

### Changed

- **Flux requirement**: Full typing summary (including clipboard and correction rates) requires **synheart-flux v0.3.0 or later**. Older `.so` versions will return 0 for these rates.
- **FluxBridge**: Typing events now send `number_of_backspace`, `number_of_delete`, `number_of_copy`, `number_of_paste`, `number_of_cut` so Flux can compute the rates.
- **Logging**: Removed temporary debug logging in FluxBridge (conversion and extraction) and SessionTracker (Flux call details).

### Documentation

- README: Version 0.4.0, Flux v0.3.0+ recommendation, typing summary example with clipboard/correction rates, new troubleshooting entry for rates stuck at 0.
- SYNHEART_FLUX_INTEGRATION: Flux v0.3.0 release link, "Typing Summary from Flux" section, clipboard/correction formulas and troubleshooting.

## [0.3.0] - 2025-01-21

### BREAKING CHANGES

- **Flux is now required**: The SDK now requires `synheart-flux` (Rust library) for all behavioral and typing metric calculations. The SDK will fail to initialize if Flux libraries are not available.
- **Removed native Kotlin calculations**: All native Kotlin calculation functions have been removed (~500+ lines). All metrics are now computed exclusively by `synheart-flux`.

### Added

- **JNI Bridge**: Added C++ JNI bridge (`libflux_jni_bridge.so`) for calling Rust functions from Kotlin
- **CMake Build Configuration**: Added CMake support for building the JNI bridge library
- **Flux Integration**: Complete integration with `synheart-flux` for HSI-compliant metric computation
- **Detailed Typing Metrics**: Enhanced typing event conversion to include all metrics required by Flux (`typing_tap_count`, `mean_inter_tap_interval_ms`, `typing_burstiness`, etc.)
- **APP_SWITCH Event Type**: Added `APP_SWITCH` to `BehaviorEventType` enum for internal tracking (events are sent to Flux but not displayed in UI)

### Changed

- **FluxBridge**: Updated to load both `libsynheart_flux.so` and `libflux_jni_bridge.so`
- **SessionTracker**: Now uses Flux exclusively for all metric calculations via `computeBehavioralMetricsWithFlux()`
- **Metric Extraction**: Behavioral and typing metrics are now extracted from Flux HSI JSON output
- **Error Handling**: SDK throws `IllegalStateException` if Flux is not available (no fallback)
- **APP_SWITCH Events**: App switch events are tracked internally and sent to Flux for task switch calculations, but are not counted as one of the 6 displayed event types and are filtered out from UI event lists
- **Motion State Inference**: Improved label extraction from ONNX model output with enhanced logging for debugging
- **Event Display**: APP_SWITCH events are automatically filtered out from event timeline displays in example app

### Removed

- **Native Calculation Functions**: Removed all native Kotlin calculation functions:
  - `computeBehavioralMetrics()`
  - `computeBurstiness()`
  - `computeIdleRatio()`
  - `computeFragmentedIdleRatio()`
  - `computeScrollJitterRate()`
  - `computeDeepFocusBlocks()`
  - `computeTypingSessionSummary()`
  - `computeTypingSessionSummaryObject()`
- **Unused Imports**: Removed `kotlin.math.exp` and `kotlin.math.sqrt` imports

### Migration Guide

If you're upgrading from version 0.2.0:

1. **Install Flux Libraries**: Download and install `synheart-flux-android-jniLibs.tar.gz` from [synheart-flux releases](https://github.com/synheart-ai/synheart-flux/releases)
2. **Update Build Configuration**: Ensure your `build.gradle` includes CMake configuration (already included in the SDK)
3. **Remove Fallback Code**: Remove any code that checks `isFluxAvailable` and provides fallback behavior - Flux is now required
4. **Update API Calls**: `FluxBridge.isAvailable` is now a function: `FluxBridge.isAvailable()`

### Documentation

- Updated `SYNHEART_FLUX_INTEGRATION.md` to reflect Flux as required
- Updated `README.md` installation instructions
- Added migration notes

## [0.3.1] - 2026-02-04

### Changed

- **Publishing coordinates**: Publish as `ai.synheart:synheart-behavior` (previously `ai.synheart:behavior`)
- **License metadata**: Updated Maven Central POM license to Apache-2.0

## [0.2.0] - 2025-01-09

### Added

- **Typing Event Tracking**: Comprehensive typing session metrics including speed, cadence, burstiness, and deep typing detection
- **Motion State Inference**: ML-based activity recognition (LAYING, MOVING, SITTING, STANDING) using ONNX Runtime
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
