# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-05-15

Aggregation-refactor pass. The SDK is now a thin event producer plus a
small set of cheap real-time stats; per-session aggregates and ML-scored
fields are computed by a downstream consumer that subscribes to the
event stream.

### Breaking
- `BehaviorSessionSummary.behavioralMetrics` is now `null` by default.
  Window-level metrics (`interactionIntensity`, `taskSwitchRate`,
  `burstiness`, `behavioralDistractionScore`, `focusHint`,
  `fragmentedIdleRatio`, `scrollJitterRate`, `deepFocusBlocks`) are
  populated by a downstream consumer, not the SDK.
- `BehaviorSessionSummary.typingSessionSummary` is now `null`. Per-typing-
  session metrics still ride on each `BehaviorEvent.typing` for
  downstream aggregation.
- `NotificationSummary.notificationIgnoreRate` and
  `notificationClusteringIndex` are no longer computed; they are emitted
  as `0.0` for a downstream consumer to overwrite. Raw counts
  (`notificationCount`, `notificationIgnored`, `callCount`,
  `callIgnored`) remain unchanged.

### Removed
- `computeNotificationClusteringIndex` and the in-tracker behavioral-
  metric computation helpers.

### Changed
- Example app renders "Computed downstream from the event stream" when
  `behavioralMetrics` is `null`.
- Tests inverted to assert that the SDK no longer computes the
  aggregates locally.

## [0.4.1] - 2026-05-07

Initial open-source release of the Synheart Behavior SDK for Android.

The SDK collects privacy-preserving behavioral signals (taps, scrolls,
swipes, app switches, idle gaps, typing session counts) on Android.
No text, content, or PII is captured. Per-session raw counts are exposed
on `BehaviorSessionSummary`; real-time stats on `BehaviorStats`. On-device
motion-state inference is performed by `MotionStateInference` using a
bundled SVC model.

### Public surface
- `SynheartBehavior`, `BehaviorConfig`, `BehaviorEvent`,
  `BehaviorEventType`, `BehaviorSession`, `BehaviorSessionSummary`,
  `BehaviorStats`, `BehaviorError`.
- `onEvent: Flow<BehaviorEvent>` for real-time behavioral events;
  session-tracking API with summaries; manual stats polling via
  `getCurrentStats()`.
- Permission helpers for Android's notification listener and
  `READ_PHONE_STATE` flows.
- `MotionStateInference` runs on-device when `enableMotionLite` is set.

### Platform support
- Android API 26+ (Android 8.0+)
- Kotlin 2.0+, AGP 8.2+, Gradle 8.10+

[Unreleased]: https://github.com/synheart-ai/synheart-behavior-kotlin/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.5.0
[0.4.1]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.4.1
