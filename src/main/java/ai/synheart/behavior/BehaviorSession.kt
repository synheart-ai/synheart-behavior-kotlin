package ai.synheart.behavior

/**
 * Summary statistics for a completed behavioral session.
 */
data class BehaviorSessionSummary(
    /**
     * Unique session ID.
     */
    val sessionId: String,

    /**
     * Start timestamp in milliseconds since epoch.
     */
    val startTimestamp: Long,

    /**
     * End timestamp in milliseconds since epoch.
     */
    val endTimestamp: Long,

    /**
     * Total session duration in milliseconds.
     */
    val duration: Long,

    /**
     * Total number of events captured during this session.
     */
    val eventCount: Int = 0,

    /**
     * Average typing cadence (keys per second) during session.
     */
    val averageTypingCadence: Double? = null,

    /**
     * Average scroll velocity during session.
     */
    val averageScrollVelocity: Double? = null,

    /**
     * Number of app switches during session.
     */
    val appSwitchCount: Int = 0,

    /**
     * Session stability index (0.0 to 1.0).
     */
    val stabilityIndex: Double? = null,

    /**
     * Fragmentation index (0.0 to 1.0).
     */
    val fragmentationIndex: Double? = null
) {
    init {
        require(endTimestamp >= startTimestamp) {
            "endTimestamp must be >= startTimestamp: end=$endTimestamp, start=$startTimestamp"
        }
        require(duration >= 0) { "duration must be non-negative, got: $duration" }
        require(eventCount >= 0) { "eventCount must be non-negative, got: $eventCount" }
        require(appSwitchCount >= 0) { "appSwitchCount must be non-negative, got: $appSwitchCount" }
        stabilityIndex?.let {
            require(it in 0.0..1.0) { "stabilityIndex must be in [0.0, 1.0], got: $it" }
        }
        fragmentationIndex?.let {
            require(it in 0.0..1.0) { "fragmentationIndex must be in [0.0, 1.0], got: $it" }
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "session_id" to sessionId,
            "start_timestamp" to startTimestamp,
            "end_timestamp" to endTimestamp,
            "duration" to duration,
            "event_count" to eventCount,
            "average_typing_cadence" to averageTypingCadence,
            "average_scroll_velocity" to averageScrollVelocity,
            "app_switch_count" to appSwitchCount,
            "stability_index" to stabilityIndex,
            "fragmentation_index" to fragmentationIndex,
        )
    }
}

