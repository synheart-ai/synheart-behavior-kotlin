package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import ai.synheart.behavior.BehaviorWindowFeatures
import ai.synheart.behavior.WindowType
import kotlin.math.*

/**
 * Extracts normalized behavior features from event windows.
 *
 * Computes all features from the Behavior → HSI Fusion Table.
 */
@android.annotation.SuppressLint("NewApi")
internal class BehaviorFeatureExtractor {
    companion object {
        // Maximum expected values for normalization
        const val MAX_TAP_RATE = 10.0 // taps per second
        const val MAX_KEYSTROKE_RATE = 8.0 // keystrokes per second
        const val MAX_SCROLL_VELOCITY = 2000.0 // pixels per second
        const val MAX_SWITCH_RATE = 2.0 // switches per minute
        const val MAX_NOTIFICATION_RATE = 5.0 // notifications per minute
    }

    /** Extract features from events in a window. */
    /** Extract features from events in a window. */
    fun extractFeatures(
            events: List<BehaviorEvent>,
            windowType: WindowType,
            windowDurationMs: Long
    ): BehaviorWindowFeatures {
        if (events.isEmpty()) {
            return BehaviorWindowFeatures.zero(windowType)
        }

        val windowDurationSeconds = windowDurationMs / 1000.0

        // Filter events by type (matching Flutter implementation)
        val tapEvents = filterEvents(events, listOf(BehaviorEventType.TAP))
        val notificationEvents = filterEvents(events, listOf(BehaviorEventType.NOTIFICATION))
        val scrollEvents = filterEvents(events, listOf(BehaviorEventType.SCROLL))

        // Counts (matching Flutter logic)
        val tapCount = tapEvents.size

        // For keystroke rate: count tap events that are NOT long press
        // Flutter logic: tapEvents.where((e) { !e.metrics['long_press'] })
        val keystrokeCount =
                tapEvents.count { event ->
                    val longPress = event.metrics["long_press"] as? Boolean ?: false
                    !longPress
                }

        // App switches: In Flutter, these are tracked separately via AttentionSignalCollector
        // For now, we'll need to add app switch event type if needed
        val switchCount = 0 // TODO: Add app switch event type if needed

        // Normalize rates
        val tapRate = tapCount / windowDurationSeconds
        val tapRateNorm = if (tapCount > 0) normalize(tapRate, MAX_TAP_RATE) else 0.0

        val keystrokeRate = keystrokeCount / windowDurationSeconds
        val keystrokeRateNorm =
                if (keystrokeCount > 0) normalize(keystrokeRate, MAX_KEYSTROKE_RATE) else 0.0

        val scrollVelocityNorm = computeAverageScrollVelocity(scrollEvents)
        val switchRateNorm =
                normalize(switchCount / (windowDurationSeconds / 60.0), MAX_SWITCH_RATE)

        val notificationCount = notificationEvents.size
        val notifRateNorm =
                normalize(notificationCount / (windowDurationSeconds / 60.0), MAX_NOTIFICATION_RATE)

        val notifOpenEvents =
                notificationEvents.filter {
                    val action = it.metrics["action"] as? String
                    action == "opened" || action == "answered"
                }
        val notifOpenRateNorm =
                normalize(
                        notifOpenEvents.size / (windowDurationSeconds / 60.0),
                        MAX_NOTIFICATION_RATE
                )
        val notificationScore = (0.6 * notifRateNorm + 0.4 * notifOpenRateNorm).coerceIn(0.0, 1.0)

        // Compute derived features
        val idleRatio = computeIdleRatio(events, windowDurationMs)
        val burstiness = computeBurstiness(events, windowDurationMs)
        val sessionFragmentation = computeFragmentation(events, windowDurationMs)
        // For typing cadence stability: use tap events that are not long press
        val keystrokeEvents =
                tapEvents.filter { event ->
                    val longPress = event.metrics["long_press"] as? Boolean ?: false
                    !longPress
                }
        val typingCadenceStability =
                computeTypingCadenceStability(keystrokeEvents, windowDurationMs)
        val scrollCadenceStability = computeScrollCadenceStability(scrollEvents, windowDurationMs)
        val interactionIntensity =
                computeInteractionIntensity(
                        tapRateNorm,
                        keystrokeRateNorm,
                        scrollVelocityNorm,
                        idleRatio
                )

        // MLP inference
        val mlpInput =
                listOf(
                        tapRateNorm,
                        keystrokeRateNorm,
                        scrollVelocityNorm,
                        idleRatio,
                        switchRateNorm,
                        burstiness,
                        sessionFragmentation,
                        notifRateNorm,
                        notifOpenRateNorm,
                        notificationScore,
                        typingCadenceStability,
                        scrollCadenceStability
                )
        val mlpOutput = inferMLP(mlpInput)
        val distractionScore = mlpOutput[0]
        val focusHint = mlpOutput[1]

        return BehaviorWindowFeatures(
                tapRateNorm = tapRateNorm,
                keystrokeRateNorm = keystrokeRateNorm,
                scrollVelocityNorm = scrollVelocityNorm,
                idleRatio = idleRatio,
                switchRateNorm = switchRateNorm,
                burstiness = burstiness,
                sessionFragmentation = sessionFragmentation,
                notifRateNorm = notifRateNorm,
                notifOpenRateNorm = notifOpenRateNorm,
                notificationScore = notificationScore,
                typingCadenceStability = typingCadenceStability,
                scrollCadenceStability = scrollCadenceStability,
                interactionIntensity = interactionIntensity,
                distractionScore = distractionScore,
                focusHint = focusHint,
                windowType = windowType,
                timestamp = System.currentTimeMillis()
        )
    }

    private fun normalize(value: Double, maxValue: Double): Double {
        return (value / maxValue).coerceIn(0.0, 1.0)
    }

    private fun filterEvents(
            events: List<BehaviorEvent>,
            eventTypes: List<BehaviorEventType>
    ): List<BehaviorEvent> {
        return events.filter { event: BehaviorEvent -> eventTypes.contains(event.eventType) }
    }

    private fun computeAverageScrollVelocity(scrollEvents: List<BehaviorEvent>): Double {
        if (scrollEvents.isEmpty()) return 0.0

        var totalVelocity = 0.0
        var count = 0

        for (event in scrollEvents) {
            // Flutter uses "velocity" key in metrics
            val velocity = event.metrics["velocity"] as? Number
            if (velocity != null && velocity.toDouble() > 0) {
                totalVelocity += velocity.toDouble()
                count++
            }
        }

        if (count == 0) return 0.0
        val avgVelocity = totalVelocity / count
        return normalize(avgVelocity, MAX_SCROLL_VELOCITY)
    }

    private fun computeIdleRatio(events: List<BehaviorEvent>, windowMs: Long): Double {
        if (events.size < 2) return 0.0

        var totalIdleTime = 0.0
        for (i in 1 until events.size) {
            val prevTime =
                    try {
                        java.time.Instant.parse(events[i - 1].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val currTime =
                    try {
                        java.time.Instant.parse(events[i].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val gap = currTime - prevTime
            // Consider gaps > 2 seconds as idle
            if (gap > 2000) {
                totalIdleTime += (gap - 2000)
            }
        }

        return (totalIdleTime / windowMs).coerceIn(0.0, 1.0)
    }

    private fun computeBurstiness(events: List<BehaviorEvent>, windowMs: Long): Double {
        if (events.size < 2) return 0.0

        val intervals = mutableListOf<Long>()
        for (i in 1 until events.size) {
            val prevTime =
                    try {
                        java.time.Instant.parse(events[i - 1].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val currTime =
                    try {
                        java.time.Instant.parse(events[i].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            intervals.add(currTime - prevTime)
        }

        if (intervals.isEmpty()) return 0.0

        val mean = intervals.average()
        if (mean == 0.0) return 0.0

        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        // Burstiness formula: (σ - μ)/(σ + μ), remapped to [0, 1]
        val burstinessRaw = (stdDev - mean) / (stdDev + mean)
        return ((burstinessRaw + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun computeFragmentation(events: List<BehaviorEvent>, windowMs: Long): Double {
        if (events.isEmpty()) return 0.0

        var interruptions = 0
        for (event in events) {
            if (event.eventType == BehaviorEventType.NOTIFICATION ||
                            event.eventType == BehaviorEventType.CALL
            ) {
                interruptions++
            }
        }

        // Count large gaps (> 5 seconds) as interruptions
        for (i in 1 until events.size) {
            val time1 =
                    try {
                        java.time.Instant.parse(events[i].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val time2 =
                    try {
                        java.time.Instant.parse(events[i - 1].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val gap = time1 - time2
            if (gap > 5000) {
                interruptions++
            }
        }

        val windowDurationMinutes = windowMs / (60 * 1000.0)
        val fragmentationRate = interruptions / (windowDurationMinutes * 10.0) // 10 per minute max
        return fragmentationRate.coerceIn(0.0, 1.0)
    }

    private fun computeTypingCadenceStability(
            keystrokeEvents: List<BehaviorEvent>,
            windowMs: Long
    ): Double {
        if (keystrokeEvents.size < 2) return 0.0

        val intervals = mutableListOf<Long>()
        // Note: Logic assumes events are sorted by timestamp. If not, sorting is needed.
        // Assuming events input to extractFeatures are sorted (WindowAggregator should do this)
        for (i in 1 until keystrokeEvents.size) {
            val time1 =
                    try {
                        java.time.Instant.parse(keystrokeEvents[i].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val time2 =
                    try {
                        java.time.Instant.parse(keystrokeEvents[i - 1].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            intervals.add(time1 - time2)
        }

        if (intervals.isEmpty()) return 0.0

        val mean = intervals.average()
        if (mean == 0.0) return 0.0

        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0

        // Stability: exp(-α * cv), where α = 1.0 (matching Flutter)
        return exp(-1.0 * cv).coerceIn(0.0, 1.0)
    }

    private fun computeScrollCadenceStability(
            scrollEvents: List<BehaviorEvent>,
            windowMs: Long
    ): Double {
        if (scrollEvents.size < 2) return 0.0

        val intervals = mutableListOf<Long>()
        for (i in 1 until scrollEvents.size) {
            val time1 =
                    try {
                        java.time.Instant.parse(scrollEvents[i].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            val time2 =
                    try {
                        java.time.Instant.parse(scrollEvents[i - 1].timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            intervals.add(time1 - time2)
        }

        if (intervals.isEmpty()) return 0.0

        val mean = intervals.average()
        if (mean == 0.0) return 0.0

        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0

        // Stability: exp(-β * cv), where β = 1.0 (matching Flutter)
        return exp(-1.0 * cv).coerceIn(0.0, 1.0)
    }

    private fun computeInteractionIntensity(
            tapRateNorm: Double,
            keystrokeRateNorm: Double,
            scrollVelocityNorm: Double,
            idleRatio: Double
    ): Double {
        // Formula: sigmoid(weighted combination)
        val weights = listOf(0.3, 0.3, 0.2, 0.2) // tap, key, scroll, idle
        val weightedSum =
                weights[0] * tapRateNorm +
                        weights[1] * keystrokeRateNorm +
                        weights[2] * scrollVelocityNorm - weights[3] * idleRatio

        // Sigmoid activation: 1 / (1 + exp(-x))
        // Scale input to reasonable range
        val sigmoidInput = weightedSum * 5.0
        val intensity = 1.0 / (1.0 + exp(-sigmoidInput))

        return intensity.coerceIn(0.0, 1.0)
    }

    private fun inferMLP(features: List<Double>): List<Double> {
        if (features.size != 12) return listOf(0.0, 0.0)

        // Matching Flutter's simple on-device MLP heuristic
        val distractionScore =
                (features[0] * 0.08 + // tap_rate
                        features[1] * 0.08 + // keystroke_rate
                                features[2] * 0.04 + // scroll_velocity
                                features[3] * 0.18 + // idle_ratio
                                features[4] * 0.22 + // switch_rate
                                features[5] * 0.08 + // burstiness
                                features[6] * 0.12 + // fragmentation
                                features[7] * 0.04 + // notif_rate
                                features[8] * 0.03 + // notif_open_rate
                                features[9] * 0.05 + // notification_score
                                (1.0 - features[10]) * 0.05 + // inverse typing stability
                                (1.0 - features[11]) * 0.03) // inverse scroll stability
                        .coerceIn(0.0, 1.0)

        val focusHint = (1.0 - distractionScore * 0.8).coerceIn(0.0, 1.0)

        return listOf(distractionScore, focusHint)
    }

    private fun countEvents(events: List<BehaviorEvent>, eventTypes: List<BehaviorEventType>): Int {
        return events.count { event: BehaviorEvent -> eventTypes.contains(event.eventType) }
    }
}
