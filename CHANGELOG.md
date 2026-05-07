# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.2] - 2026-05-07

Open-source launch release.

The SDK is a pure behavioral signal collector. Higher-level behavioral
inference (HSI fusion, focus / distraction modeling, rolling baselines)
lives in the Synheart Core SDK, which consumes the events this package
emits. This SDK has no native runtime dependency.

### Public surface
The SDK collects privacy-preserving behavioral signals (taps, scrolls,
swipes, app switches, idle gaps, typing session counts) on Android. No
text, content, or PII is captured. Per-session aggregates are exposed
on `BehaviorSessionSummary` and real-time stats on `BehaviorStats`.

- `SynheartBehavior`, `BehaviorConfig`, `BehaviorEvent`,
  `BehaviorEventType`, `BehaviorSession`, `BehaviorSessionSummary`,
  `BehavioralMetrics`, `TypingSessionSummary`, `NotificationSummary`,
  `MotionState`, `BehaviorStats`, `SynheartNotificationListenerService`.
- `Flow<BehaviorEvent>` and callback-based event handlers.
- Session-tracking API with summaries; manual stats polling.
- On-demand metrics for ended sessions:
  `calculateMetricsForTimeRange()`.
- Optional on-device motion classification (LAYING / MOVING / SITTING /
  STANDING) via ONNX Runtime when `enableMotionLite` is on.

### Changed
- `MotionFeatureExtractor`, `MotionSignalCollector`, `WindowType`,
  `Triple3D`, and `GravityResult` are now `internal` — implementation
  detail of motion inference, not public API.
- README rewritten to mirror the Flutter SDK's structure (source-
  available banner, full Privacy & Compliance breakdown, Architecture
  diagram, Troubleshooting section, "Not a Medical Device" notice).

### Added
- `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`,
  `.github/CODEOWNERS`, `.github/ISSUE_TEMPLATE/`,
  `.github/pull_request_template.md`,
  `.github/workflows/close-external-prs.yml`, `.github/dependabot.yml`.
- `example/GUIDE.md` walkthrough mirroring the Flutter example guide.

### Platform support
- Android API 21+ (Android 5.0+)
- Kotlin 2.0+, JDK 17, Gradle 8.0+

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

## [0.3.1] - 2026-02-04

### Changed

- **Publishing coordinates**: Publish as `ai.synheart:synheart-behavior` (previously `ai.synheart:behavior`)
- **License metadata**: Updated Maven Central POM license to Apache-2.0

## [0.3.0] - 2025-01-21

### BREAKING CHANGES

- **Flux is now required**: The SDK now requires `synheart-flux` for all behavioral and typing metric calculations. The SDK will fail to initialize if Flux libraries are not available.
- **Removed native Kotlin calculations**: All native Kotlin calculation functions have been removed (~500+ lines). All metrics are now computed exclusively by `synheart-flux`.

### Added

- **JNI Bridge**: Added C++ JNI bridge (`libflux_jni_bridge.so`) for calling native Flux functions from Kotlin
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

[Unreleased]: https://github.com/synheart-ai/synheart-behavior-kotlin/compare/v0.4.2...HEAD
[0.4.2]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.4.2
[0.4.1]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.4.1
[0.4.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.4.0
[0.3.1]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.3.1
[0.3.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.3.0
[0.2.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.2.0
