package ai.synheart.behavior

/**
 * Types of behavioral events that can be emitted by the SDK.
 */
enum class BehaviorEventType {
    /** Keystroke timing events */
    TYPING_CADENCE,
    TYPING_BURST,

    /** Scroll dynamics events */
    SCROLL_VELOCITY,
    SCROLL_ACCELERATION,
    SCROLL_JITTER,
    SCROLL_STOP,

    /** Gesture activity events */
    TAP_RATE,
    LONG_PRESS_RATE,
    DRAG_VELOCITY,

    /** App switching events */
    APP_SWITCH,
    FOREGROUND_DURATION,

    /** Idle gap events */
    IDLE_GAP,
    MICRO_IDLE,
    MID_IDLE,
    TASK_DROP_IDLE,

    /** Session stability events */
    SESSION_STABILITY,
    FRAGMENTATION_INDEX,

    /** Motion-lite events (optional) */
    ORIENTATION_SHIFT,
    SHAKE_PATTERN,
    MICRO_MOVEMENT
}

/**
 * A single behavioral event emitted by the SDK.
 */
data class BehaviorEvent(
    /**
     * Unique session ID for this event.
     */
    val sessionId: String,

    /**
     * Timestamp in milliseconds since epoch.
     */
    val timestamp: Long,

    /**
     * Type of behavioral event.
     */
    val type: BehaviorEventType,

    /**
     * Event payload containing signal-specific data.
     */
    val payload: Map<String, Any>
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "session_id" to sessionId,
            "timestamp" to timestamp,
            "type" to type.name.lowercase(),
            "payload" to payload,
        )
    }
}

