package ai.synheart.behavior

/**
 * Represents an active behavioral tracking session. Matches Flutter SDK BehaviorSession structure.
 */
class BehaviorSession(
        /** Unique session ID. */
        val sessionId: String,

        /** Start timestamp in milliseconds since epoch. */
        val startTimestamp: Long,

        /** Callback function to end this session. */
        private val endCallback: (String) -> BehaviorSessionSummary
) {
        /** End this session and generate a summary. */
        fun end(): BehaviorSessionSummary {
                return endCallback(sessionId)
        }

        /** Get the current duration of this session in milliseconds. */
        fun getCurrentDuration(): Long {
                return System.currentTimeMillis() - startTimestamp
        }
}

/** Motion state information. Matches Flutter SDK MotionState structure. */
data class MotionState(
        /** Array of states for each window, e.g., ["laying", "moving", "sitting", "standing"] */
        val state: List<String>,
        /** Most common state, e.g., "sitting" */
        val majorState: String,
        /** Percentage of major state, e.g., 0.5 */
        val majorStatePct: Double,
        /** ML model identifier, e.g., "motion_state_svc_classifier_v0.1" */
        val mlModel: String,
        /** Confidence score (0.0 to 1.0, but can be outside range for SVC models) */
        val confidence: Double
) {
        companion object {
                fun fromJson(json: Map<String, Any>): MotionState {
                        return MotionState(
                                state = (json["state"] as? List<*>)?.mapNotNull { it?.toString() }
                                                ?: emptyList(),
                                majorState = (json["major_state"] as? String) ?: "unknown",
                                majorStatePct = (json["major_state_pct"] as? Number)?.toDouble()
                                                ?: 0.0,
                                mlModel = (json["ml_model"] as? String)
                                                ?: "motion_state_svc_classifier_v0.1",
                                confidence = (json["confidence"] as? Number)?.toDouble() ?: 0.0
                        )
                }
        }

        fun toJson(): Map<String, Any> {
                return mapOf(
                        "state" to state,
                        "major_state" to majorState,
                        "major_state_pct" to majorStatePct,
                        "ml_model" to mlModel,
                        "confidence" to confidence
                )
        }
}

/** Raw motion data point (accelerometer and gyroscope arrays per timestamp). */
data class MotionDataPoint(
        /** ISO 8601 timestamp for this data point (5-second window) */
        val timestamp: String,
        /** ML features extracted from raw sensor data (561 features) */
        /** Feature names match the format from features.txt (e.g., "tBodyAcc-mean()-X") */
        val features: Map<String, Double>
) {
        companion object {
                fun fromJson(json: Map<String, Any>): MotionDataPoint {
                        val featuresMap =
                                (json["features"] as? Map<*, *>)?.let {
                                        it.mapKeys { it.key.toString() }.mapValues {
                                                (it.value as? Number)?.toDouble() ?: 0.0
                                        }
                                }
                                        ?: emptyMap<String, Double>()
                        return MotionDataPoint(
                                timestamp = (json["timestamp"] as? String) ?: "",
                                features = featuresMap
                        )
                }
        }

        fun toJson(): Map<String, Any> {
                return mapOf("timestamp" to timestamp, "features" to features)
        }
}

/** Device context information. */
data class DeviceContext(
        val avgScreenBrightness: Double, // Average of start and end brightness (0.0 to 1.0)
        val startOrientation: String, // "portrait" or "landscape"
        val orientationChanges: Int // Number of orientation changes during session
)

/** Activity summary for the session. */
data class ActivitySummary(val totalEvents: Int, val appSwitchCount: Int)

/** Deep focus block period. */
data class DeepFocusBlock(
        val startAt: String, // ISO 8601 timestamp
        val endAt: String, // ISO 8601 timestamp
        val durationMs: Int // Duration in milliseconds
)

/** Behavioral metrics for the session. */
data class BehavioralMetrics(
        val interactionIntensity: Double,
        val taskSwitchRate: Double,
        val taskSwitchCost: Int, // in milliseconds
        val idleTimeRatio: Double,
        val activeTimeRatio: Double,
        val notificationLoad: Double,
        val burstiness: Double,
        val behavioralDistractionScore: Double,
        val focusHint: Double,
        val fragmentedIdleRatio: Double,
        val scrollJitterRate: Double,
        val deepFocusBlocks: List<DeepFocusBlock>
)

/** Notification summary for the session. */
data class NotificationSummary(
        val notificationCount: Int,
        val notificationIgnored: Int,
        val notificationIgnoreRate: Double,
        val notificationClusteringIndex: Double,
        val callCount: Int,
        val callIgnored: Int
)

/** System state information. */
data class SystemState(
        val internetState: Boolean,
        val doNotDisturb: Boolean,
        val charging: Boolean
)

/** Typing metrics for a single typing session (keyboard open to close). */
data class TypingMetrics(
        /** ISO 8601 timestamp */
        val startAt: String,
        /** ISO 8601 timestamp */
        val endAt: String,
        /** Duration in seconds */
        val duration: Int,
        /** Whether this is a deep typing session */
        val deepTyping: Boolean,
        /** Total number of keyboard tap events */
        val typingTapCount: Int,
        /** Tap events per second */
        val typingSpeed: Double,
        /** Average time between taps */
        val meanInterTapIntervalMs: Double,
        /** Variability in timing between taps */
        val typingCadenceVariability: Double,
        /** Normalized rhythmic consistency (0.0-1.0) */
        val typingCadenceStability: Double,
        /** Number of pauses exceeding threshold */
        val typingGapCount: Int,
        /** Proportion of intervals that are gaps */
        val typingGapRatio: Double,
        /** Dispersion of inter-tap intervals */
        val typingBurstiness: Double,
        /** Fraction of window with active typing */
        val typingActivityRatio: Double,
        /** Composite engagement measure */
        val typingInteractionIntensity: Double
) {
        companion object {
                fun fromJson(json: Map<String, Any>): TypingMetrics {
                        return TypingMetrics(
                                startAt = (json["start_at"] as? String) ?: "",
                                endAt = (json["end_at"] as? String) ?: "",
                                duration = (json["duration"] as? Number)?.toInt() ?: 0,
                                deepTyping = (json["deep_typing"] as? Boolean) ?: false,
                                typingTapCount = (json["typing_tap_count"] as? Number)?.toInt()
                                                ?: 0,
                                typingSpeed = (json["typing_speed"] as? Number)?.toDouble() ?: 0.0,
                                meanInterTapIntervalMs =
                                        (json["mean_inter_tap_interval_ms"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingCadenceVariability =
                                        (json["typing_cadence_variability"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingCadenceStability =
                                        (json["typing_cadence_stability"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingGapCount = (json["typing_gap_count"] as? Number)?.toInt()
                                                ?: 0,
                                typingGapRatio = (json["typing_gap_ratio"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingBurstiness =
                                        (json["typing_burstiness"] as? Number)?.toDouble() ?: 0.0,
                                typingActivityRatio =
                                        (json["typing_activity_ratio"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingInteractionIntensity =
                                        (json["typing_interaction_intensity"] as? Number)
                                                ?.toDouble()
                                                ?: 0.0
                        )
                }
        }

        fun toJson(): Map<String, Any> {
                return mapOf(
                        "start_at" to startAt,
                        "end_at" to endAt,
                        "duration" to duration,
                        "deep_typing" to deepTyping,
                        "typing_tap_count" to typingTapCount,
                        "typing_speed" to typingSpeed,
                        "mean_inter_tap_interval_ms" to meanInterTapIntervalMs,
                        "typing_cadence_variability" to typingCadenceVariability,
                        "typing_cadence_stability" to typingCadenceStability,
                        "typing_gap_count" to typingGapCount,
                        "typing_gap_ratio" to typingGapRatio,
                        "typing_burstiness" to typingBurstiness,
                        "typing_activity_ratio" to typingActivityRatio,
                        "typing_interaction_intensity" to typingInteractionIntensity
                )
        }
}

/** Typing session summary aggregated across all typing sessions in the app session. */
data class TypingSessionSummary(
        /** Number of distinct typing sessions */
        val typingSessionCount: Int,
        /** Average taps per typing session */
        val averageKeystrokesPerSession: Double,
        /** Average duration per typing session (seconds) */
        val averageTypingSessionDuration: Double,
        /** Average typing speed across all sessions */
        val averageTypingSpeed: Double,
        /** Average gap duration between keystrokes */
        val averageTypingGap: Double,
        /** Average inter-tap interval across all sessions */
        val averageInterTapInterval: Double,
        /** Overall cadence stability */
        val typingCadenceStability: Double,
        /** Overall burstiness measure */
        val burstinessOfTyping: Double,
        /** Total time spent typing (seconds) */
        val totalTypingDuration: Int,
        /** Ratio of typing time to total session time */
        val activeTypingRatio: Double,
        /** Typing's contribution to overall intensity */
        val typingContributionToInteractionIntensity: Double,
        /** Number of deep typing blocks */
        val deepTypingBlocks: Int,
        /** Measure of typing fragmentation */
        val typingFragmentation: Double,
        /** List of individual typing sessions */
        val individualTypingSessions: List<TypingMetrics>
) {
        companion object {
                fun fromJson(json: Map<String, Any>): TypingSessionSummary {
                        val individualSessions =
                                (json["typing_metrics"] as? List<*>)?.mapNotNull {
                                        if (it is Map<*, *>) {
                                                val map =
                                                        it.mapKeys { it.key.toString() }.mapValues {
                                                                it.value as Any
                                                        }
                                                TypingMetrics.fromJson(map)
                                        } else null
                                }
                                        ?: emptyList()
                        return TypingSessionSummary(
                                typingSessionCount =
                                        (json["typing_session_count"] as? Number)?.toInt() ?: 0,
                                averageKeystrokesPerSession =
                                        (json["average_keystrokes_per_session"] as? Number)
                                                ?.toDouble()
                                                ?: 0.0,
                                averageTypingSessionDuration =
                                        (json["average_typing_session_duration"] as? Number)
                                                ?.toDouble()
                                                ?: 0.0,
                                averageTypingSpeed =
                                        (json["average_typing_speed"] as? Number)?.toDouble()
                                                ?: 0.0,
                                averageTypingGap =
                                        (json["average_typing_gap"] as? Number)?.toDouble() ?: 0.0,
                                averageInterTapInterval =
                                        (json["average_inter_tap_interval"] as? Number)?.toDouble()
                                                ?: 0.0,
                                typingCadenceStability =
                                        (json["typing_cadence_stability"] as? Number)?.toDouble()
                                                ?: 0.0,
                                burstinessOfTyping =
                                        (json["burstiness_of_typing"] as? Number)?.toDouble()
                                                ?: 0.0,
                                totalTypingDuration =
                                        (json["total_typing_duration"] as? Number)?.toInt() ?: 0,
                                activeTypingRatio =
                                        (json["active_typing_ratio"] as? Number)?.toDouble() ?: 0.0,
                                typingContributionToInteractionIntensity =
                                        (json["typing_contribution_to_interaction_intensity"] as?
                                                        Number)
                                                ?.toDouble()
                                                ?: 0.0,
                                deepTypingBlocks = (json["deep_typing_blocks"] as? Number)?.toInt()
                                                ?: 0,
                                typingFragmentation =
                                        (json["typing_fragmentation"] as? Number)?.toDouble()
                                                ?: 0.0,
                                individualTypingSessions = individualSessions
                        )
                }
        }

        fun toJson(): Map<String, Any> {
                return mapOf(
                        "typing_session_count" to typingSessionCount,
                        "average_keystrokes_per_session" to averageKeystrokesPerSession,
                        "average_typing_session_duration" to averageTypingSessionDuration,
                        "average_typing_speed" to averageTypingSpeed,
                        "average_typing_gap" to averageTypingGap,
                        "average_inter_tap_interval" to averageInterTapInterval,
                        "typing_cadence_stability" to typingCadenceStability,
                        "burstiness_of_typing" to burstinessOfTyping,
                        "total_typing_duration" to totalTypingDuration,
                        "active_typing_ratio" to activeTypingRatio,
                        "typing_contribution_to_interaction_intensity" to
                                typingContributionToInteractionIntensity,
                        "deep_typing_blocks" to deepTypingBlocks,
                        "typing_fragmentation" to typingFragmentation,
                        "typing_metrics" to individualTypingSessions.map { it.toJson() }
                )
        }
}

/**
 * Summary statistics for a completed behavioral session. Matches Flutter SDK BehaviorSessionSummary
 * structure exactly.
 */
data class BehaviorSessionSummary(
        /** Unique session ID. */
        val sessionId: String,

        /** Start timestamp in ISO 8601 format. */
        val startAt: String,

        /** End timestamp in ISO 8601 format. */
        val endAt: String,

        /** Whether this is a micro session (< 30 seconds). */
        val microSession: Boolean,

        /** OS version string (e.g., "Android 12.3", "iOS 17.0"). */
        val os: String,

        /** App identifier. */
        val appId: String? = null,

        /** App name (display name). */
        val appName: String? = null,

        /** Session spacing in milliseconds (time since last app use). */
        val sessionSpacing: Int,

        /** Motion state information. */
        val motionState: MotionState? = null,

        /** Device context information. */
        val deviceContext: DeviceContext,

        /** Activity summary. */
        val activitySummary: ActivitySummary,

        /** Behavioral metrics. */
        val behavioralMetrics: BehavioralMetrics,

        /** Notification summary. */
        val notificationSummary: NotificationSummary,

        /** System state. */
        val systemState: SystemState,

        /** Typing session summary. */
        val typingSessionSummary: TypingSessionSummary? = null,

        /** Raw motion data (accelerometer and gyroscope arrays per timestamp). */
        val motionData: List<MotionDataPoint>? = null
) {
        /** Get session duration in milliseconds. */
        val durationMs: Int
                @android.annotation.SuppressLint("NewApi")
                get() {
                        return try {
                                val start = java.time.Instant.parse(startAt)
                                val end = java.time.Instant.parse(endAt)
                                java.time.Duration.between(start, end).toMillis().toInt()
                        } catch (e: Exception) {
                                0
                        }
                }
}
