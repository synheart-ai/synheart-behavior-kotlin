package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorSessionSummary
import ai.synheart.behavior.BehaviorStats
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages session state and tracks aggregated statistics across all collectors.
 */
internal class SessionTracker(
    val sessionId: String
) {
    private val startTimestamp = System.currentTimeMillis()
    private val eventCount = AtomicInteger(0)
    private val events = ConcurrentHashMap<String, MutableList<BehaviorEvent>>()

    // Aggregated statistics
    private val typingCadences = mutableListOf<Double>()
    private val scrollVelocities = mutableListOf<Double>()
    private var appSwitchCount = 0
    private var stabilityIndex: Double? = null
    private var fragmentationIndex: Double? = null

    @Synchronized
    fun recordEvent(event: BehaviorEvent) {
        eventCount.incrementAndGet()
        events.getOrPut(event.type.name) { mutableListOf() }.add(event)

        // Update aggregated statistics
        updateAggregatedStats(event)
    }

    @Synchronized
    private fun updateAggregatedStats(event: BehaviorEvent) {
        when (event.type.name) {
            "TYPING_CADENCE" -> {
                (event.payload["keys_per_second"] as? Number)?.toDouble()?.let {
                    typingCadences.add(it)
                }
            }
            "SCROLL_VELOCITY" -> {
                (event.payload["velocity_px_per_sec"] as? Number)?.toDouble()?.let {
                    scrollVelocities.add(it)
                }
            }
            "APP_SWITCH" -> {
                appSwitchCount++
            }
            "SESSION_STABILITY" -> {
                (event.payload["stability_index"] as? Number)?.toDouble()?.let {
                    stabilityIndex = it
                }
            }
            "FRAGMENTATION_INDEX" -> {
                (event.payload["fragmentation_index"] as? Number)?.toDouble()?.let {
                    fragmentationIndex = it
                }
            }
        }
    }

    fun getEventCount(): Int = eventCount.get()

    @Synchronized
    fun getCurrentStats(
        inputStats: Map<String, Any?>,
        attentionStats: Map<String, Any?>,
        motionStats: Map<String, Any?>
    ): BehaviorStats {
        return BehaviorStats(
            typingCadence = inputStats["typingCadence"] as? Double,
            interKeyLatency = inputStats["interKeyLatency"] as? Double,
            burstLength = inputStats["burstLength"] as? Int,
            scrollVelocity = inputStats["scrollVelocity"] as? Double,
            scrollAcceleration = inputStats["scrollAcceleration"] as? Double,
            scrollJitter = inputStats["scrollJitter"] as? Double,
            tapRate = inputStats["tapRate"] as? Double,
            appSwitchesPerMinute = (attentionStats["appSwitchesPerMinute"] as? Int) ?: 0,
            foregroundDuration = attentionStats["foregroundDuration"] as? Double,
            idleGapSeconds = attentionStats["idleGapSeconds"] as? Double,
            stabilityIndex = attentionStats["stabilityIndex"] as? Double,
            fragmentationIndex = attentionStats["fragmentationIndex"] as? Double,
            timestamp = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun getSessionSummary(
        inputSummary: Map<String, Any?>,
        attentionSummary: Map<String, Any?>,
        motionSummary: Map<String, Any?>
    ): BehaviorSessionSummary {
        val endTimestamp = System.currentTimeMillis()
        val duration = endTimestamp - startTimestamp

        return BehaviorSessionSummary(
            sessionId = sessionId,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            duration = duration,
            eventCount = eventCount.get(),
            averageTypingCadence = if (typingCadences.isNotEmpty()) typingCadences.average() else null,
            averageScrollVelocity = if (scrollVelocities.isNotEmpty()) scrollVelocities.average() else null,
            appSwitchCount = appSwitchCount,
            stabilityIndex = stabilityIndex,
            fragmentationIndex = fragmentationIndex
        )
    }

    fun getStartTimestamp(): Long = startTimestamp
}
