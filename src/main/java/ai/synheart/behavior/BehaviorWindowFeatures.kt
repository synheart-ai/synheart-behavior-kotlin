package ai.synheart.behavior

/**
 * Derived behavior features for a time window.
 *
 * Contains all normalized features computed from raw behavioral events, plus MLP inference outputs
 * (distraction_score and focus_hint). Matches the Behavior → HSI Fusion Table specification.
 */
data class BehaviorWindowFeatures(
        /** Normalized tap rate (0.0 to 1.0). */
        val tapRateNorm: Double,

        /** Normalized keystroke rate (0.0 to 1.0). */
        val keystrokeRateNorm: Double,

        /** Normalized scroll velocity (0.0 to 1.0). */
        val scrollVelocityNorm: Double,

        /** Ratio of idle time to total window time (0.0 to 1.0). */
        val idleRatio: Double,

        /** Normalized app switch rate (0.0 to 1.0). */
        val switchRateNorm: Double,

        /** Burstiness metric (0.0 to 1.0) - measures activity clustering. */
        val burstiness: Double,

        /** Session fragmentation index (0.0 to 1.0). */
        val sessionFragmentation: Double,

        /** Normalized notification received rate (0.0 to 1.0). */
        val notifRateNorm: Double,

        /** Normalized notification opened rate (0.0 to 1.0). */
        val notifOpenRateNorm: Double,

        /** Notification score: 0.6 * notif_rate_norm + 0.4 * notif_open_rate_norm */
        val notificationScore: Double,

        /** Typing cadence stability (0.0 to 1.0) - consistency of typing rhythm. */
        val typingCadenceStability: Double,

        /** Scroll cadence stability (0.0 to 1.0) - consistency of scroll rhythm. */
        val scrollCadenceStability: Double,

        /** Interaction intensity: weighted combination of tap/key/scroll minus idle. */
        val interactionIntensity: Double,

        /** Behavioral distraction score from MLP (0.0 to 1.0). */
        val distractionScore: Double,

        /** Behavioral focus hint from MLP (0.0 to 1.0). */
        val focusHint: Double,

        /** Window type (short or long). */
        val windowType: WindowType,

        /** Timestamp when these features were computed. */
        val timestamp: Long
) {
    companion object {
        /** Create zero-initialized features (used when no data available). */
        fun zero(windowType: WindowType): BehaviorWindowFeatures {
            return BehaviorWindowFeatures(
                    tapRateNorm = 0.0,
                    keystrokeRateNorm = 0.0,
                    scrollVelocityNorm = 0.0,
                    idleRatio = 0.0,
                    switchRateNorm = 0.0,
                    burstiness = 0.0,
                    sessionFragmentation = 0.0,
                    notifRateNorm = 0.0,
                    notifOpenRateNorm = 0.0,
                    notificationScore = 0.0,
                    typingCadenceStability = 0.0,
                    scrollCadenceStability = 0.0,
                    interactionIntensity = 0.0,
                    distractionScore = 0.0,
                    focusHint = 0.0,
                    windowType = windowType,
                    timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Convert to HSI (Human State Inference) payload format.
     *
     * Returns a JSON-compatible map matching the Behavior → HSI Fusion Table specification.
     */
    fun toHSIPayload(
            userId: String,
            deviceId: String,
            behaviorVersion: String,
            consentBehavior: Boolean = true
    ): Map<String, Any> {
        // Calculate window duration based on window type
        val windowDurationSeconds = if (windowType == WindowType.SHORT) 30 else 300 // 30s or 5m
        val windowStart = timestamp / 1000 // Convert to seconds
        val windowEnd = windowStart + windowDurationSeconds

        return mapOf(
                "window_start" to windowStart,
                "window_end" to windowEnd,
                "user_id" to userId,
                "device_id" to deviceId,
                "behavior_version" to behaviorVersion,
                "features" to
                        mapOf(
                                "tap_rate_norm" to tapRateNorm,
                                "keystroke_rate_norm" to keystrokeRateNorm,
                                "scroll_velocity_norm" to scrollVelocityNorm,
                                "typing_cadence_stability" to typingCadenceStability,
                                "scroll_cadence_stability" to scrollCadenceStability,
                                "idle_ratio" to idleRatio,
                                "switch_rate_norm" to switchRateNorm,
                                "session_fragmentation" to sessionFragmentation,
                                "burstiness" to burstiness,
                                "notif_rate_norm" to notifRateNorm,
                                "notif_open_rate_norm" to notifOpenRateNorm,
                                "notification_load" to
                                        notificationScore, // Note: spec uses "notification_load"
                                "distraction_score" to distractionScore,
                                "behavioral_focus_hint" to
                                        focusHint // Note: spec uses "behavioral_focus_hint"
                        ),
                "consent" to mapOf("behavior" to consentBehavior),
                "source" to "behavior_sdk"
        )
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
                "tap_rate_norm" to tapRateNorm,
                "keystroke_rate_norm" to keystrokeRateNorm,
                "scroll_velocity_norm" to scrollVelocityNorm,
                "idle_ratio" to idleRatio,
                "switch_rate_norm" to switchRateNorm,
                "burstiness" to burstiness,
                "session_fragmentation" to sessionFragmentation,
                "notif_rate_norm" to notifRateNorm,
                "notif_open_rate_norm" to notifOpenRateNorm,
                "notification_score" to notificationScore,
                "typing_cadence_stability" to typingCadenceStability,
                "scroll_cadence_stability" to scrollCadenceStability,
                "interaction_intensity" to interactionIntensity,
                "distraction_score" to distractionScore,
                "focus_hint" to focusHint,
                "window_type" to windowType.name.lowercase(),
                "timestamp" to timestamp
        )
    }
}
