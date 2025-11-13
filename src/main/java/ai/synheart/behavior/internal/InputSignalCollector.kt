package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.view.View
import android.view.ViewTreeObserver
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collector for input interaction signals: keystroke timing, scroll dynamics, gestures.
 * Monitors user input patterns to extract biobehavioral markers.
 */
internal class InputSignalCollector(
    private val sessionId: String
) : SignalCollector {

    private var eventCallback: ((BehaviorEvent) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isCollecting = false

    // Keystroke tracking
    private val keyTimestamps = ConcurrentLinkedQueue<Long>()
    private var lastKeyTimestamp: Long? = null
    private var currentBurstCount = 0
    private val burstThresholdMs = 200L // Keys within 200ms are part of a burst

    // Scroll tracking
    private val scrollEvents = ConcurrentLinkedQueue<ScrollEvent>()
    private var lastScrollTimestamp: Long? = null
    private var lastScrollY = 0f

    // Gesture tracking
    private val tapTimestamps = ConcurrentLinkedQueue<Long>()
    private val longPressTimestamps = ConcurrentLinkedQueue<Long>()
    private val dragEvents = ConcurrentLinkedQueue<DragEvent>()

    // Statistics
    private var totalKeystrokes = 0
    private var totalTaps = 0
    private var totalLongPresses = 0
    private var totalScrolls = 0

    override fun start() {
        isCollecting = true
        // Start periodic event emission for rolling statistics
        scope.launch {
            while (isCollecting) {
                delay(5000) // Emit every 5 seconds
                emitTypingCadenceEvent()
                emitScrollVelocityEvent()
                emitTapRateEvent()
            }
        }
    }

    override fun stop() {
        isCollecting = false
        scope.cancel()
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventCallback = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        return mapOf(
            "typingCadence" to calculateTypingCadence(),
            "interKeyLatency" to calculateInterKeyLatency(),
            "burstLength" to currentBurstCount,
            "scrollVelocity" to calculateScrollVelocity(),
            "scrollAcceleration" to calculateScrollAcceleration(),
            "scrollJitter" to calculateScrollJitter(),
            "tapRate" to calculateTapRate()
        )
    }

    override fun getSessionSummary(): Map<String, Any?> {
        return mapOf(
            "averageTypingCadence" to calculateAverageTypingCadence(),
            "averageScrollVelocity" to calculateAverageScrollVelocity(),
            "totalKeystrokes" to totalKeystrokes,
            "totalTaps" to totalTaps,
            "totalScrolls" to totalScrolls
        )
    }

    override fun resetSession() {
        keyTimestamps.clear()
        scrollEvents.clear()
        tapTimestamps.clear()
        longPressTimestamps.clear()
        dragEvents.clear()
        lastKeyTimestamp = null
        lastScrollTimestamp = null
        currentBurstCount = 0
        totalKeystrokes = 0
        totalTaps = 0
        totalLongPresses = 0
        totalScrolls = 0
    }

    /**
     * Called when a key event is detected (e.g., via AccessibilityService or EditText monitoring).
     */
    fun onKeyEvent(timestamp: Long = System.currentTimeMillis()) {
        keyTimestamps.offer(timestamp)
        totalKeystrokes++

        // Track bursts
        lastKeyTimestamp?.let { lastTime ->
            if (timestamp - lastTime <= burstThresholdMs) {
                currentBurstCount++
            } else {
                if (currentBurstCount > 1) {
                    emitTypingBurstEvent(currentBurstCount)
                }
                currentBurstCount = 1
            }
        } ?: run {
            currentBurstCount = 1
        }

        lastKeyTimestamp = timestamp

        // Clean old timestamps (keep last 60 seconds)
        cleanOldTimestamps(keyTimestamps, timestamp, 60000)
    }

    /**
     * Called when a scroll event is detected.
     */
    fun onScrollEvent(deltaY: Float, timestamp: Long = System.currentTimeMillis()) {
        val lastTime = lastScrollTimestamp ?: timestamp
        val deltaTime = (timestamp - lastTime).toDouble() / 1000.0 // seconds

        if (deltaTime > 0) {
            val velocity = deltaY / deltaTime.toFloat() // pixels per second
            scrollEvents.offer(ScrollEvent(timestamp, velocity, deltaY))
            totalScrolls++
        }

        lastScrollTimestamp = timestamp
        lastScrollY = deltaY

        // Clean old events (keep last 10 seconds)
        cleanOldScrollEvents(timestamp, 10000)

        // Emit scroll stop event if no scroll for 500ms
        scope.launch {
            delay(500)
            if (lastScrollTimestamp?.let { it + 500 <= System.currentTimeMillis() } == true) {
                emitScrollStopEvent()
            }
        }
    }

    /**
     * Called when a tap gesture is detected.
     */
    fun onTapEvent(timestamp: Long = System.currentTimeMillis()) {
        tapTimestamps.offer(timestamp)
        totalTaps++
        cleanOldTimestamps(tapTimestamps, timestamp, 60000)
    }

    /**
     * Called when a long press is detected.
     */
    fun onLongPressEvent(timestamp: Long = System.currentTimeMillis()) {
        longPressTimestamps.offer(timestamp)
        totalLongPresses++
        cleanOldTimestamps(longPressTimestamps, timestamp, 60000)
    }

    /**
     * Called when a drag gesture is detected.
     */
    fun onDragEvent(startX: Float, startY: Float, endX: Float, endY: Float,
                    duration: Long, timestamp: Long = System.currentTimeMillis()) {
        val distance = sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
        val velocity = if (duration > 0) distance / (duration / 1000.0f) else 0f
        dragEvents.offer(DragEvent(timestamp, velocity, distance))
        emitDragVelocityEvent(velocity)

        // Clean old events
        cleanOldDragEvents(timestamp, 60000)
    }

    // Event emission methods
    private fun emitTypingCadenceEvent() {
        val cadence = calculateTypingCadence()
        if (cadence != null) {
            emitEvent(
                BehaviorEventType.TYPING_CADENCE,
                mapOf(
                    "keys_per_second" to cadence,
                    "inter_key_latency_ms" to calculateInterKeyLatency()
                )
            )
        }
    }

    private fun emitTypingBurstEvent(burstLength: Int) {
        emitEvent(
            BehaviorEventType.TYPING_BURST,
            mapOf("burst_length" to burstLength)
        )
    }

    private fun emitScrollVelocityEvent() {
        val velocity = calculateScrollVelocity()
        if (velocity != null) {
            emitEvent(
                BehaviorEventType.SCROLL_VELOCITY,
                mapOf(
                    "velocity_px_per_sec" to velocity,
                    "acceleration_px_per_sec2" to calculateScrollAcceleration(),
                    "jitter" to calculateScrollJitter()
                )
            )
        }
    }

    private fun emitScrollStopEvent() {
        emitEvent(
            BehaviorEventType.SCROLL_STOP,
            mapOf("timestamp" to System.currentTimeMillis())
        )
    }

    private fun emitTapRateEvent() {
        val tapRate = calculateTapRate()
        if (tapRate != null && tapRate > 0) {
            emitEvent(
                BehaviorEventType.TAP_RATE,
                mapOf(
                    "taps_per_second" to tapRate,
                    "long_press_rate" to calculateLongPressRate()
                )
            )
        }
    }

    private fun emitDragVelocityEvent(velocity: Float) {
        emitEvent(
            BehaviorEventType.DRAG_VELOCITY,
            mapOf("velocity_px_per_sec" to velocity)
        )
    }

    // Calculation methods
    private fun calculateTypingCadence(): Double? {
        val recent = keyTimestamps.filter { it > System.currentTimeMillis() - 10000 }
        if (recent.isEmpty()) return null
        val duration = (recent.maxOrNull()!! - recent.minOrNull()!!) / 1000.0
        return if (duration > 0) recent.size / duration else null
    }

    private fun calculateAverageTypingCadence(): Double? {
        if (keyTimestamps.isEmpty()) return null
        val duration = (keyTimestamps.maxOrNull()!! - keyTimestamps.minOrNull()!!) / 1000.0
        return if (duration > 0) keyTimestamps.size / duration else null
    }

    private fun calculateInterKeyLatency(): Double? {
        val list = keyTimestamps.toList()
        if (list.size < 2) return null
        val latencies = list.zipWithNext { a, b -> (b - a).toDouble() }
        return latencies.average()
    }

    private fun calculateScrollVelocity(): Double? {
        val recent = scrollEvents.filter { it.timestamp > System.currentTimeMillis() - 2000 }
        if (recent.isEmpty()) return null
        return recent.map { it.velocity.toDouble() }.average()
    }

    private fun calculateAverageScrollVelocity(): Double? {
        if (scrollEvents.isEmpty()) return null
        return scrollEvents.map { abs(it.velocity.toDouble()) }.average()
    }

    private fun calculateScrollAcceleration(): Double? {
        val recent = scrollEvents.filter { it.timestamp > System.currentTimeMillis() - 2000 }.toList()
        if (recent.size < 2) return null
        val velocities = recent.map { it.velocity }
        val accelerations = velocities.zipWithNext { a, b -> b - a }
        return accelerations.map { it.toDouble() }.average()
    }

    private fun calculateScrollJitter(): Double? {
        val recent = scrollEvents.filter { it.timestamp > System.currentTimeMillis() - 2000 }
        if (recent.size < 2) return null
        val velocities = recent.map { it.velocity.toDouble() }
        val mean = velocities.average()
        val variance = velocities.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun calculateTapRate(): Double? {
        val recent = tapTimestamps.filter { it > System.currentTimeMillis() - 10000 }
        if (recent.isEmpty()) return null
        val duration = (recent.maxOrNull()!! - recent.minOrNull()!!) / 1000.0
        return if (duration > 0) recent.size / duration else null
    }

    private fun calculateLongPressRate(): Double? {
        val recent = longPressTimestamps.filter { it > System.currentTimeMillis() - 60000 }
        if (recent.isEmpty()) return null
        return recent.size / 60.0 // per minute
    }

    private fun emitEvent(type: BehaviorEventType, payload: Map<String, Any?>) {
        eventCallback?.invoke(
            BehaviorEvent(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                type = type,
                payload = payload.filterValues { it != null }.mapValues { it.value as Any }
            )
        )
    }

    private fun cleanOldTimestamps(queue: ConcurrentLinkedQueue<Long>, currentTime: Long, maxAgeMs: Long) {
        while (queue.peek()?.let { currentTime - it > maxAgeMs } == true) {
            queue.poll()
        }
    }

    private fun cleanOldScrollEvents(currentTime: Long, maxAgeMs: Long) {
        while (scrollEvents.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            scrollEvents.poll()
        }
    }

    private fun cleanOldDragEvents(currentTime: Long, maxAgeMs: Long) {
        while (dragEvents.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            dragEvents.poll()
        }
    }

    private data class ScrollEvent(val timestamp: Long, val velocity: Float, val deltaY: Float)
    private data class DragEvent(val timestamp: Long, val velocity: Float, val distance: Float)
}
