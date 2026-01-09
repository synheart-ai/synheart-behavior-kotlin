package ai.synheart.behavior

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

/** Types of behavioral events that can be emitted by the SDK. Matches Flutter SDK event types. */
enum class BehaviorEventType {
        SCROLL,
        TAP,
        SWIPE,
        NOTIFICATION,
        CALL,
        TYPING
}

/** A single behavioral event emitted by the SDK. Matches Flutter SDK BehaviorEvent structure. */
data class BehaviorEvent(
        /** Unique event ID. */
        val eventId: String = "evt_${System.currentTimeMillis()}",

        /** Unique session ID for this event. */
        val sessionId: String,

        /** Timestamp in ISO 8601 format (e.g., "2025-03-14T10:15:23.456Z"). */
        val timestamp: String,

        /** Type of behavioral event. */
        val eventType: BehaviorEventType,

        /** Event-specific metrics (matches Flutter's "metrics" field). */
        val metrics: Map<String, Any>
) {
        companion object {
                private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

                /** Create a scroll event. */
                fun scroll(
                        sessionId: String,
                        velocity: Double,
                        acceleration: Double,
                        direction: String,
                        directionReversal: Boolean,
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.SCROLL,
                                metrics =
                                        mapOf(
                                                "velocity" to velocity,
                                                "acceleration" to acceleration,
                                                "direction" to direction,
                                                "direction_reversal" to directionReversal
                                        )
                        )
                }

                /** Create a tap event. */
                fun tap(
                        sessionId: String,
                        tapDurationMs: Int,
                        longPress: Boolean,
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.TAP,
                                metrics =
                                        mapOf(
                                                "tap_duration_ms" to tapDurationMs,
                                                "long_press" to longPress
                                        )
                        )
                }

                /** Create a swipe event. */
                fun swipe(
                        sessionId: String,
                        direction: String,
                        distancePx: Double,
                        durationMs: Int,
                        velocity: Double,
                        acceleration: Double,
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.SWIPE,
                                metrics =
                                        mapOf(
                                                "direction" to direction,
                                                "distance_px" to distancePx,
                                                "duration_ms" to durationMs,
                                                "velocity" to velocity,
                                                "acceleration" to acceleration
                                        )
                        )
                }

                /** Create a notification event. */
                fun notification(
                        sessionId: String,
                        action: String, // "ignored", "opened", "answered", "dismissed"
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.NOTIFICATION,
                                metrics = mapOf("action" to action)
                        )
                }

                /** Create a call event. */
                fun call(
                        sessionId: String,
                        action: String, // "ignored", "opened", "answered", "dismissed"
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.CALL,
                                metrics = mapOf("action" to action)
                        )
                }

                /** Create a typing event. */
                fun typing(
                        sessionId: String,
                        typingTapCount: Int,
                        typingSpeed: Double,
                        meanInterTapIntervalMs: Double,
                        typingCadenceVariability: Double,
                        typingCadenceStability: Double,
                        typingGapCount: Int,
                        typingGapRatio: Double,
                        typingBurstiness: Double,
                        typingActivityRatio: Double,
                        typingInteractionIntensity: Double,
                        durationSeconds: Int,
                        startAt: String,
                        endAt: String,
                        deepTyping: Boolean,
                        eventId: String? = null,
                        timestamp: String? = null
                ): BehaviorEvent {
                        return BehaviorEvent(
                                eventId = eventId ?: "evt_${System.currentTimeMillis()}",
                                sessionId = sessionId,
                                timestamp = timestamp ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = BehaviorEventType.TYPING,
                                metrics =
                                        mapOf(
                                                "typing_tap_count" to typingTapCount,
                                                "typing_speed" to typingSpeed,
                                                "mean_inter_tap_interval_ms" to
                                                        meanInterTapIntervalMs,
                                                "typing_cadence_variability" to
                                                        typingCadenceVariability,
                                                "typing_cadence_stability" to
                                                        typingCadenceStability,
                                                "typing_gap_count" to typingGapCount,
                                                "typing_gap_ratio" to typingGapRatio,
                                                "typing_burstiness" to typingBurstiness,
                                                "typing_activity_ratio" to typingActivityRatio,
                                                "typing_interaction_intensity" to
                                                        typingInteractionIntensity,
                                                "duration" to durationSeconds,
                                                "start_at" to startAt,
                                                "end_at" to endAt,
                                                "deep_typing" to deepTyping
                                        )
                        )
                }

                /** Parse from JSON (for compatibility with native SDK events). */
                fun fromJson(json: Map<String, Any>): BehaviorEvent {
                        val eventData =
                                (json["event"] as? Map<*, *>)?.let {
                                        it.mapKeys { it.key.toString() }.mapValues { it.value }
                                }
                                        ?: json

                        val eventTypeStr =
                                (eventData["event_type"] as? String)
                                        ?: (eventData["type"] as? String) ?: "tap"
                        val eventType =
                                BehaviorEventType.values().find {
                                        it.name.lowercase() == eventTypeStr.lowercase()
                                }
                                        ?: BehaviorEventType.TAP

                        val metricsMap =
                                (eventData["metrics"] as? Map<*, *>)?.let {
                                        it.mapKeys { it.key.toString() }.mapValues {
                                                it.value as Any
                                        }
                                }
                                        ?: (eventData["payload"] as? Map<*, *>)?.let {
                                                it.mapKeys { it.key.toString() }.mapValues {
                                                        it.value as Any
                                                }
                                        }
                                                ?: emptyMap<String, Any>()

                        return BehaviorEvent(
                                eventId = (eventData["event_id"] as? String)
                                                ?: "evt_${System.currentTimeMillis()}",
                                sessionId = (eventData["session_id"] as? String) ?: "",
                                timestamp = (eventData["timestamp"] as? String)
                                                ?: ISO_FORMATTER.format(Instant.now()),
                                eventType = eventType,
                                metrics = metricsMap
                        )
                }
        }

        /** Convert to JSON format (wrapped in "event" object, matching Flutter). */
        fun toJson(): Map<String, Any> {
                return mapOf(
                        "event" to
                                mapOf(
                                        "event_id" to eventId,
                                        "session_id" to sessionId,
                                        "timestamp" to timestamp,
                                        "event_type" to eventType.name.lowercase(),
                                        "metrics" to metrics
                                )
                )
        }

        /** Convert to legacy format for backward compatibility. */
        fun toLegacyJson(): Map<String, Any> {
                val timestampMs =
                        try {
                                Instant.parse(timestamp).toEpochMilli()
                        } catch (e: Exception) {
                                System.currentTimeMillis()
                        }
                return mapOf(
                        "session_id" to sessionId,
                        "timestamp" to timestampMs,
                        "type" to eventType.name.lowercase(),
                        "payload" to metrics
                )
        }

        override fun toString(): String {
                return "BehaviorEvent(eventId=$eventId, sessionId=$sessionId, type=$eventType, timestamp=$timestamp)"
        }
}
