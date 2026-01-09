package ai.synheart.behavior

/** Configuration for initializing the Synheart Behavioral SDK. */
data class BehaviorConfig(
        /** Enable input interaction signals (keystroke timing, scroll dynamics, gestures). */
        val enableInputSignals: Boolean = true,

        /**
         * Enable attention and multitasking signals (app switching, idle gaps, session stability).
         */
        val enableAttentionSignals: Boolean = true,

        /**
         * Enable motion-lite signals (device orientation, shake patterns, micro-movement). Note:
         * This is optional and may have higher battery impact.
         */
        val enableMotionLite: Boolean = false,

        /** Custom session ID prefix. If null, auto-generated. */
        val sessionIdPrefix: String? = null,

        /** Event batch size for streaming. Default: 10 events per batch. */
        val eventBatchSize: Int = 10,

        /**
         * Maximum idle gap duration in seconds before considering task dropped. Default: 10
         * seconds.
         */
        /**
         * Maximum idle gap duration in seconds before considering task dropped. Default: 10
         * seconds.
         */
        val maxIdleGapSeconds: Double = 10.0,

        /** Optional user identifier. */
        val userId: String? = null,

        /** Optional device identifier. */
        val deviceId: String? = null,

        /** SDK version. */
        val behaviorVersion: String = "1.0.0",

        /** Whether behavior tracking consent is granted. */
        val consentBehavior: Boolean = true
) {
    init {
        require(eventBatchSize > 0) { "eventBatchSize must be positive, got: $eventBatchSize" }
        require(maxIdleGapSeconds > 0.0) {
            "maxIdleGapSeconds must be positive, got: $maxIdleGapSeconds"
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
                "enableInputSignals" to enableInputSignals,
                "enableAttentionSignals" to enableAttentionSignals,
                "enableMotionLite" to enableMotionLite,
                "sessionIdPrefix" to (sessionIdPrefix ?: ""),
                "eventBatchSize" to eventBatchSize,
                "maxIdleGapSeconds" to maxIdleGapSeconds,
                "userId" to (userId ?: ""),
                "deviceId" to (deviceId ?: ""),
                "behaviorVersion" to behaviorVersion,
                "consentBehavior" to consentBehavior
        )
    }
}
