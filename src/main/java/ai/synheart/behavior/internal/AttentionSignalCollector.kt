package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * Collector for attention and multitasking signals: app switching, idle gaps, session stability.
 * Monitors user attention patterns and task fragmentation.
 */
internal class AttentionSignalCollector(
    private val sessionId: String,
    private val application: Application,
    private val maxIdleGapSeconds: Double
) : SignalCollector {

    private var eventCallback: ((BehaviorEvent) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isCollecting = false

    // App lifecycle tracking
    private val appSwitchTimestamps = ConcurrentLinkedQueue<Long>()
    private var foregroundStartTime: Long? = null
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var currentIdleGapStart: Long? = null

    // Idle gap tracking
    private val idleGaps = ConcurrentLinkedQueue<IdleGap>()
    private val microIdleThreshold = 2000L // 2 seconds
    private val midIdleThreshold = 5000L // 5 seconds

    // Session stability tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var totalForegroundTime: Long = 0
    private var totalIdleTime: Long = 0
    private var taskSwitchCount = 0

    // Activity lifecycle callback
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            onAppForegrounded()
        }
        override fun onActivityPaused(activity: Activity) {
            onAppBackgrounded()
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    override fun start() {
        isCollecting = true
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        sessionStartTime = System.currentTimeMillis()
        foregroundStartTime = System.currentTimeMillis()

        // Start monitoring idle gaps
        scope.launch {
            while (isCollecting) {
                delay(1000) // Check every second
                checkForIdleGap()
            }
        }

        // Start periodic stability reporting
        scope.launch {
            while (isCollecting) {
                delay(30000) // Emit every 30 seconds
                emitSessionStabilityEvent()
                emitFragmentationIndexEvent()
            }
        }
    }

    override fun stop() {
        isCollecting = false
        application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        scope.cancel()

        // Record final foreground duration
        foregroundStartTime?.let {
            totalForegroundTime += (System.currentTimeMillis() - it)
        }
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventCallback = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        val currentTime = System.currentTimeMillis()
        val recentSwitches = appSwitchTimestamps.count { it > currentTime - 60000 }
        val currentForegroundDuration = foregroundStartTime?.let {
            (currentTime - it) / 1000.0
        }
        val currentIdleGap = currentIdleGapStart?.let {
            (currentTime - it) / 1000.0
        }

        return mapOf(
            "appSwitchesPerMinute" to recentSwitches,
            "foregroundDuration" to currentForegroundDuration,
            "idleGapSeconds" to currentIdleGap,
            "stabilityIndex" to calculateStabilityIndex(),
            "fragmentationIndex" to calculateFragmentationIndex()
        )
    }

    override fun getSessionSummary(): Map<String, Any?> {
        return mapOf(
            "appSwitchCount" to taskSwitchCount,
            "stabilityIndex" to calculateStabilityIndex(),
            "fragmentationIndex" to calculateFragmentationIndex(),
            "totalForegroundTime" to totalForegroundTime,
            "totalIdleTime" to totalIdleTime
        )
    }

    override fun resetSession() {
        appSwitchTimestamps.clear()
        idleGaps.clear()
        foregroundStartTime = System.currentTimeMillis()
        lastActivityTime = System.currentTimeMillis()
        currentIdleGapStart = null
        sessionStartTime = System.currentTimeMillis()
        totalForegroundTime = 0
        totalIdleTime = 0
        taskSwitchCount = 0
    }

    /**
     * Called when user performs any activity (typing, scrolling, tapping).
     */
    fun onUserActivity() {
        val currentTime = System.currentTimeMillis()
        val idleGapStart = currentIdleGapStart

        // Record idle gap if there was one
        if (idleGapStart != null) {
            val idleGapDuration = currentTime - idleGapStart
            recordIdleGap(idleGapDuration)
            currentIdleGapStart = null
        }

        lastActivityTime = currentTime
    }

    private fun onAppForegrounded() {
        val currentTime = System.currentTimeMillis()
        appSwitchTimestamps.offer(currentTime)
        taskSwitchCount++
        foregroundStartTime = currentTime

        emitAppSwitchEvent()

        // Clean old timestamps (keep last 60 seconds)
        cleanOldTimestamps(currentTime, 60000)
    }

    private fun onAppBackgrounded() {
        foregroundStartTime?.let {
            val duration = (System.currentTimeMillis() - it) / 1000.0
            totalForegroundTime += (System.currentTimeMillis() - it)
            emitForegroundDurationEvent(duration)
        }
        foregroundStartTime = null
    }

    private fun checkForIdleGap() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastActivity = currentTime - lastActivityTime

        if (currentIdleGapStart == null && timeSinceLastActivity > 1000) {
            // Start tracking idle gap
            currentIdleGapStart = lastActivityTime
        }

        // Check if we've exceeded max idle gap
        if (timeSinceLastActivity > (maxIdleGapSeconds * 1000).toLong()) {
            emitTaskDropIdleEvent(timeSinceLastActivity / 1000.0)
        }
    }

    private fun recordIdleGap(duration: Long) {
        val durationSeconds = duration / 1000.0
        idleGaps.offer(IdleGap(System.currentTimeMillis(), duration))
        totalIdleTime += duration

        // Emit appropriate idle gap event
        when {
            duration < microIdleThreshold -> emitMicroIdleEvent(durationSeconds)
            duration < midIdleThreshold -> emitMidIdleEvent(durationSeconds)
            else -> emitIdleGapEvent(durationSeconds)
        }

        // Clean old idle gaps (keep last 5 minutes)
        cleanOldIdleGaps(System.currentTimeMillis(), 300000)
    }

    // Event emission methods
    private fun emitAppSwitchEvent() {
        emitEvent(
            BehaviorEventType.APP_SWITCH,
            mapOf(
                "timestamp" to System.currentTimeMillis(),
                "app_switches_per_minute" to calculateAppSwitchesPerMinute()
            )
        )
    }

    private fun emitForegroundDurationEvent(duration: Double) {
        emitEvent(
            BehaviorEventType.FOREGROUND_DURATION,
            mapOf("duration_seconds" to duration)
        )
    }

    private fun emitIdleGapEvent(duration: Double) {
        emitEvent(
            BehaviorEventType.IDLE_GAP,
            mapOf("duration_seconds" to duration)
        )
    }

    private fun emitMicroIdleEvent(duration: Double) {
        emitEvent(
            BehaviorEventType.MICRO_IDLE,
            mapOf("duration_seconds" to duration)
        )
    }

    private fun emitMidIdleEvent(duration: Double) {
        emitEvent(
            BehaviorEventType.MID_IDLE,
            mapOf("duration_seconds" to duration)
        )
    }

    private fun emitTaskDropIdleEvent(duration: Double) {
        emitEvent(
            BehaviorEventType.TASK_DROP_IDLE,
            mapOf("duration_seconds" to duration)
        )
    }

    private fun emitSessionStabilityEvent() {
        val stability = calculateStabilityIndex()
        if (stability != null) {
            emitEvent(
                BehaviorEventType.SESSION_STABILITY,
                mapOf("stability_index" to stability)
            )
        }
    }

    private fun emitFragmentationIndexEvent() {
        val fragmentation = calculateFragmentationIndex()
        if (fragmentation != null) {
            emitEvent(
                BehaviorEventType.FRAGMENTATION_INDEX,
                mapOf("fragmentation_index" to fragmentation)
            )
        }
    }

    // Calculation methods
    private fun calculateAppSwitchesPerMinute(): Int {
        val currentTime = System.currentTimeMillis()
        return appSwitchTimestamps.count { it > currentTime - 60000 }
    }

    /**
     * Stability index: 1.0 = very stable (few switches, long focus), 0.0 = very unstable.
     */
    private fun calculateStabilityIndex(): Double? {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        if (sessionDuration < 10000) return null // Need at least 10 seconds

        val switchRate = taskSwitchCount / (sessionDuration / 60000.0) // switches per minute
        val avgForegroundTime = if (taskSwitchCount > 0) {
            totalForegroundTime.toDouble() / taskSwitchCount
        } else {
            sessionDuration.toDouble()
        }

        // Higher stability = fewer switches and longer foreground times
        // Normalize: assume > 10 switches/min = very unstable, < 1 switch/min = very stable
        val switchStability = max(0.0, 1.0 - (switchRate / 10.0))

        // Normalize foreground time: > 60s per task = stable, < 10s = unstable
        val foregroundStability = minOf(1.0, avgForegroundTime / 60000.0)

        return (switchStability + foregroundStability) / 2.0
    }

    /**
     * Fragmentation index: 1.0 = highly fragmented (many short idle gaps), 0.0 = continuous.
     */
    private fun calculateFragmentationIndex(): Double? {
        if (idleGaps.isEmpty()) return 0.0

        val totalGaps = idleGaps.size
        val shortGaps = idleGaps.count { it.duration < midIdleThreshold }
        val longGaps = idleGaps.count { it.duration >= midIdleThreshold }

        // More short gaps = higher fragmentation
        val fragmentationScore = if (totalGaps > 0) {
            (shortGaps.toDouble() / totalGaps) * 0.7 + (longGaps.toDouble() / totalGaps) * 1.0
        } else {
            0.0
        }

        return minOf(1.0, fragmentationScore)
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

    private fun cleanOldTimestamps(currentTime: Long, maxAgeMs: Long) {
        while (appSwitchTimestamps.peek()?.let { currentTime - it > maxAgeMs } == true) {
            appSwitchTimestamps.poll()
        }
    }

    private fun cleanOldIdleGaps(currentTime: Long, maxAgeMs: Long) {
        while (idleGaps.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            idleGaps.poll()
        }
    }

    private data class IdleGap(val timestamp: Long, val duration: Long)
}
