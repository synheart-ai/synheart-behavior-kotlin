# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-10-02

Minor bump, not a patch: the library manifest now declares the
notification listener (it merges into every host), Android samples motion
ten times faster, and call events are emitted once per call instead of two
or three times.

### Changed
- **The library manifest declares `SynheartNotificationListenerService`**
  (and `BIND_NOTIFICATION_LISTENER_SERVICE`), so hosts no longer copy the
  block. It was never registered by the library, so in most hosts the
  collector never ran and notification events reached the engine without an
  outcome. The service stays inert until the person grants notification
  access in system settings. To keep it out, a host adds
  `<service android:name="ai.synheart.behavior.SynheartNotificationListenerService" tools:node="remove" />`.
  The Play policy declaration for the permission stays with the host.

### Fixed
- **Android accelerometer at 50 Hz.** It passed `SENSOR_DELAY_NORMAL`, with a
  comment claiming ~50 Hz; that constant is ~200 ms, about 5 Hz, below the
  25 Hz the engine needs for a Ready motion baseline. It now requests an
  explicit 20 000 µs period.
- **Notification events carry `source_app`**, the posting package, from the
  listener through the collector. `reportNotification`,
  `reportNotificationIgnored` and `reportNotificationOpened` take an
  optional `sourceApp`.
- **Android: one event per call, at its outcome.** A call used to emit when
  it started ringing (`ringing`), again with its outcome (`answered` or
  `ignored`), and once more when it hung up (`ended`). A consumer counting
  call events saw every unanswered call twice and an answered call three
  times. Only the outcome is emitted now, which is what the iOS collector
  already did. Notifications are unchanged: an arrival (`received`) followed
  by its outcome.

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

[Unreleased]: https://github.com/synheart-ai/synheart-behavior-kotlin/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.5.0
[0.4.1]: https://github.com/synheart-ai/synheart-behavior-kotlin/releases/tag/v0.4.1
