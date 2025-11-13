package ai.synheart.behavior

/**
 * Rolling statistics snapshot of current behavioral signals.
 */
data class BehaviorStats(
    /**
     * Current typing cadence (keys per second).
     */
    val typingCadence: Double? = null,

    /**
     * Current inter-key latency in milliseconds.
     */
    val interKeyLatency: Double? = null,

    /**
     * Current burst length (number of keys in current burst).
     */
    val burstLength: Int? = null,

    /**
     * Current scroll velocity (pixels per second).
     */
    val scrollVelocity: Double? = null,

    /**
     * Current scroll acceleration (pixels per second squared).
     */
    val scrollAcceleration: Double? = null,

    /**
     * Current scroll jitter (variance in scroll speed).
     */
    val scrollJitter: Double? = null,

    /**
     * Current tap rate (taps per second).
     */
    val tapRate: Double? = null,

    /**
     * Number of app switches in the last minute.
     */
    val appSwitchesPerMinute: Int = 0,

    /**
     * Current foreground duration in seconds.
     */
    val foregroundDuration: Double? = null,

    /**
     * Current idle gap duration in seconds.
     */
    val idleGapSeconds: Double? = null,

    /**
     * Current session stability index (0.0 to 1.0).
     */
    val stabilityIndex: Double? = null,

    /**
     * Current fragmentation index (0.0 to 1.0).
     */
    val fragmentationIndex: Double? = null,

    /**
     * Timestamp when these stats were captured.
     */
    val timestamp: Long
) {
    init {
        require(appSwitchesPerMinute >= 0) { "appSwitchesPerMinute must be non-negative, got: $appSwitchesPerMinute" }
        stabilityIndex?.let {
            require(it in 0.0..1.0) { "stabilityIndex must be in [0.0, 1.0], got: $it" }
        }
        fragmentationIndex?.let {
            require(it in 0.0..1.0) { "fragmentationIndex must be in [0.0, 1.0], got: $it" }
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "typing_cadence" to typingCadence,
            "inter_key_latency" to interKeyLatency,
            "burst_length" to burstLength,
            "scroll_velocity" to scrollVelocity,
            "scroll_acceleration" to scrollAcceleration,
            "scroll_jitter" to scrollJitter,
            "tap_rate" to tapRate,
            "app_switches_per_minute" to appSwitchesPerMinute,
            "foreground_duration" to foregroundDuration,
            "idle_gap_seconds" to idleGapSeconds,
            "stability_index" to stabilityIndex,
            "fragmentation_index" to fragmentationIndex,
            "timestamp" to timestamp,
        )
    }
}

