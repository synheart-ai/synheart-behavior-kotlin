package ai.synheart.behavior.internal

import ai.synheart.behavior.*
import ai.synheart.behavior.TypingMetrics
import ai.synheart.behavior.TypingSessionSummary
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Manages session state and tracks all events for comprehensive summary generation. Matches Flutter
 * SDK's SessionData behavior.
 */
internal class SessionTracker(
        val sessionId: String,
        private val context: Context,
        sessionSpacing: Long = 0L
) {
    private val startTimestamp = System.currentTimeMillis()
    private val eventCount = AtomicInteger(0)

    // Store ALL events in a single list (matching Flutter SDK's SessionData.events)
    private val allEvents = ConcurrentLinkedQueue<BehaviorEvent>()

    // Session metadata
    private var appSwitchCount = 0
    private val startScreenBrightness: Float
    private val startOrientation: Int
    private var orientationChangeCount = 0
    private val startInternetState: Boolean
    private val startDoNotDisturb: Boolean
    private val startCharging: Boolean
    private val sessionSpacing: Long

    init {
        // Capture device context at session start (matching Flutter SDK)
        startScreenBrightness = getScreenBrightness()
        startOrientation = context.resources.configuration.orientation
        startInternetState = isInternetConnected()
        startDoNotDisturb = isDoNotDisturbEnabled()
        startCharging = isCharging()
        this.sessionSpacing = sessionSpacing
    }

    @Synchronized
    fun recordEvent(event: BehaviorEvent) {
        eventCount.incrementAndGet()
        allEvents.offer(event)
    }

    fun updateAppSwitchCount(count: Int) {
        appSwitchCount = count
    }

    fun onOrientationChanged(newOrientation: Int) {
        if (newOrientation != startOrientation) {
            orientationChangeCount++
        }
    }

    fun getEventCount(): Int = eventCount.get()

    fun getAllEvents(): List<BehaviorEvent> = allEvents.toList()

    @Synchronized
    fun getCurrentStats(
            inputStats: Map<String, Any?>,
            attentionStats: Map<String, Any?>,
            gestureStats: Map<String, Any?>
    ): BehaviorStats {
        return BehaviorStats(
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
            gestureSummary: Map<String, Any?>,
            motionData: List<MotionDataPoint>? = null
    ): BehaviorSessionSummary {
        val endTimestamp = System.currentTimeMillis()
        val duration = endTimestamp - startTimestamp
        val durationSeconds = duration / 1000.0
        val isoFormatter = DateTimeFormatter.ISO_INSTANT

        // Get all events sorted by timestamp
        val events =
                allEvents.sortedBy {
                    try {
                        Instant.parse(it.timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                }

        // Generate comprehensive summary matching Flutter structure
        val startAt = isoFormatter.format(Instant.ofEpochMilli(startTimestamp))
        val endAt = isoFormatter.format(Instant.ofEpochMilli(endTimestamp))
        val microSession = durationSeconds < 30.0 // < 30 seconds

        // Get OS version
        val osVersion = "Android ${Build.VERSION.RELEASE}"

        // Get app ID and name
        val appId = context.packageName
        val appName =
                try {
                    val packageManager = context.packageManager
                    val applicationInfo = packageManager.getApplicationInfo(appId, 0)
                    packageManager.getApplicationLabel(applicationInfo).toString()
                } catch (e: Exception) {
                    appId
                }

        // Calculate average screen brightness (start + end) / 2
        val endScreenBrightness = getScreenBrightness()
        val avgScreenBrightness = (startScreenBrightness + endScreenBrightness) / 2.0

        // Get orientation string
        val startOrientationStr =
                when (startOrientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> "landscape"
                    else -> "portrait"
                }

        // Get system state at end
        val endInternetState = isInternetConnected()
        val endDoNotDisturb = isDoNotDisturbEnabled()
        val endCharging = isCharging()

        // Compute notification summary from events
        val notificationEvents = events.filter { it.eventType == BehaviorEventType.NOTIFICATION }
        val notificationCount = notificationEvents.size
        val notificationIgnored =
                notificationEvents.count { (it.metrics["action"] as? String) == "ignored" }
        val notificationOpened =
                notificationEvents.count {
                    (it.metrics["action"] as? String) == "opened" ||
                            (it.metrics["action"] as? String) == "answered"
                }
        val notificationIgnoreRate =
                if (notificationCount > 0) {
                    notificationIgnored.toDouble() / notificationCount
                } else 0.0
        val notificationClusteringIndex = computeNotificationClusteringIndex(notificationEvents)

        // Compute call summary
        val callEvents = events.filter { it.eventType == BehaviorEventType.CALL }
        val callCount = callEvents.size
        val callIgnored = callEvents.count { (it.metrics["action"] as? String) == "ignored" }

        // Compute behavioral metrics locally
        val metrics = computeBehavioralMetrics(
                events,
                duration,
                startTimestamp,
                endTimestamp
        )

        // Convert metrics map to BehavioralMetrics object
        val behavioralMetrics = BehavioralMetrics(
                interactionIntensity = metrics["interaction_intensity"] as? Double ?: 0.0,
                taskSwitchRate = metrics["task_switch_rate"] as? Double ?: 0.0,
                taskSwitchCost = (metrics["task_switch_cost"] as? Number)?.toInt() ?: 0,
                idleTimeRatio = metrics["idle_time_ratio"] as? Double ?: 0.0,
                activeTimeRatio = metrics["active_time_ratio"] as? Double ?: 0.0,
                notificationLoad = metrics["notification_load"] as? Double ?: 0.0,
                burstiness = metrics["burstiness"] as? Double ?: 0.0,
                behavioralDistractionScore = metrics["behavioral_distraction_score"] as? Double ?: 0.0,
                focusHint = metrics["focus_hint"] as? Double ?: 0.0,
                fragmentedIdleRatio = metrics["fragmented_idle_ratio"] as? Double ?: 0.0,
                scrollJitterRate = metrics["scroll_jitter_rate"] as? Double ?: 0.0,
                deepFocusBlocks = parseDeepFocusBlocks(metrics["deep_focus_blocks"])
        )

        // Device Context
        val deviceContext =
                DeviceContext(
                        avgScreenBrightness = avgScreenBrightness,
                        startOrientation = startOrientationStr,
                        orientationChanges = orientationChangeCount
                )

        // Activity Summary
        val activitySummary =
                ActivitySummary(totalEvents = eventCount.get(), appSwitchCount = appSwitchCount)

        // Notification Summary
        val notificationSummary =
                NotificationSummary(
                        notificationCount = notificationCount,
                        notificationIgnored = notificationIgnored,
                        notificationIgnoreRate = notificationIgnoreRate,
                        notificationClusteringIndex = notificationClusteringIndex,
                        callCount = callCount,
                        callIgnored = callIgnored
                )

        // System State
        val systemState =
                SystemState(
                        internetState = endInternetState,
                        doNotDisturb = endDoNotDisturb,
                        charging = endCharging
                )

        // Extract typing session summary from computed metrics
        val typingSummaryMap = metrics["typing_session_summary"] as? Map<String, Any>
        val typingSessionSummary = if (typingSummaryMap != null && typingSummaryMap.isNotEmpty()) {
            convertTypingSummaryToObject(typingSummaryMap)
        } else {
            TypingSessionSummary(
                    typingSessionCount = 0,
                    averageKeystrokesPerSession = 0.0,
                    averageTypingSessionDuration = 0.0,
                    averageTypingSpeed = 0.0,
                    averageTypingGap = 0.0,
                    averageInterTapInterval = 0.0,
                    typingCadenceStability = 0.0,
                    burstinessOfTyping = 0.0,
                    totalTypingDuration = 0,
                    activeTypingRatio = 0.0,
                    typingContributionToInteractionIntensity = 0.0,
                    deepTypingBlocks = 0,
                    typingFragmentation = 0.0,
                    individualTypingSessions = emptyList()
            )
        }

        // Motion State will be computed by SynheartBehavior after inference
        val motionState: MotionState? = null

        return BehaviorSessionSummary(
                sessionId = sessionId,
                startAt = startAt,
                endAt = endAt,
                microSession = microSession,
                os = osVersion,
                appId = appId,
                appName = appName,
                sessionSpacing = sessionSpacing.toInt(),
                motionState = motionState,
                deviceContext = deviceContext,
                activitySummary = activitySummary,
                behavioralMetrics = behavioralMetrics,
                notificationSummary = notificationSummary,
                systemState = systemState,
                typingSessionSummary = typingSessionSummary,
                motionData = motionData
        )
    }

    companion object {
        private const val TAG = "SessionTracker"
    }

    /** Default metrics used when no events are available or as a baseline. */
    private fun defaultMetrics(): Map<String, Any> {
        return mapOf(
                "interaction_intensity" to 0.0,
                "task_switch_rate" to 0.0,
                "task_switch_cost" to 0,
                "idle_time_ratio" to 0.0,
                "active_time_ratio" to 1.0,
                "notification_load" to 0.0,
                "burstiness" to 0.0,
                "behavioral_distraction_score" to 0.0,
                "focus_hint" to 0.0,
                "fragmented_idle_ratio" to 0.0,
                "scroll_jitter_rate" to 0.0,
                "deep_focus_blocks" to emptyList<Map<String, Any>>(),
                "typing_session_summary" to mapOf(
                        "typing_session_count" to 0,
                        "average_keystrokes_per_session" to 0.0,
                        "average_typing_session_duration" to 0.0,
                        "average_typing_speed" to 0.0,
                        "average_typing_gap" to 0.0,
                        "average_inter_tap_interval" to 0.0,
                        "typing_cadence_stability" to 0.0,
                        "burstiness_of_typing" to 0.0,
                        "total_typing_duration" to 0,
                        "active_typing_ratio" to 0.0,
                        "typing_contribution_to_interaction_intensity" to 0.0,
                        "deep_typing_blocks" to 0,
                        "typing_fragmentation" to 0.0,
                        "clipboard_activity_rate" to 0.0,
                        "correction_rate" to 0.0,
                        "typing_metrics" to emptyList<Map<String, Any>>()
                )
        )
    }

    /**
     * Compute behavioral metrics from events locally.
     * Returns the computed metrics map.
     */
    private fun computeBehavioralMetrics(
            events: List<BehaviorEvent>,
            durationMs: Long,
            sessionStartTime: Long,
            sessionEndTime: Long
    ): Map<String, Any> {
        if (events.isEmpty() || durationMs <= 0) {
            return defaultMetrics()
        }

        val durationSeconds = durationMs / 1000.0
        val isoFormatter = DateTimeFormatter.ISO_INSTANT

        // Parse event timestamps
        val eventTimestamps = events.mapNotNull { event ->
            try { Instant.parse(event.timestamp).toEpochMilli() } catch (e: Exception) { null }
        }.sorted()

        // Event type counts
        val tapEvents = events.filter { it.eventType == BehaviorEventType.TAP }
        val scrollEvents = events.filter { it.eventType == BehaviorEventType.SCROLL }
        val typingEvents = events.filter { it.eventType == BehaviorEventType.TYPING }
        val notificationEvents = events.filter { it.eventType == BehaviorEventType.NOTIFICATION }
        val appSwitchEvents = events.filter { it.eventType == BehaviorEventType.APP_SWITCH }

        // Interaction intensity: weighted sum of event rates
        val tapRate = if (durationSeconds > 0) tapEvents.size / durationSeconds else 0.0
        val scrollRate = if (durationSeconds > 0) scrollEvents.size / durationSeconds else 0.0
        val typingRate = if (durationSeconds > 0) typingEvents.size / durationSeconds else 0.0
        val interactionIntensity = (0.3 * tapRate + 0.3 * scrollRate + 0.4 * typingRate).coerceIn(0.0, 1.0)

        // Task switch rate
        val taskSwitchRate = if (durationSeconds > 0) appSwitchEvents.size / (durationSeconds / 60.0) else 0.0

        // Task switch cost: average background duration from app switch events
        val bgDurations = appSwitchEvents.mapNotNull { (it.metrics["background_duration_ms"] as? Number)?.toInt() }
        val taskSwitchCost = if (bgDurations.isNotEmpty()) bgDurations.average().toInt() else 0

        // Idle time: gaps > 2 seconds between events
        var totalIdleMs = 0L
        for (i in 1 until eventTimestamps.size) {
            val gap = eventTimestamps[i] - eventTimestamps[i - 1]
            if (gap > 2000) totalIdleMs += gap
        }
        val idleTimeRatio = if (durationMs > 0) (totalIdleMs.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0
        val activeTimeRatio = 1.0 - idleTimeRatio

        // Notification load
        val notificationLoad = if (durationSeconds > 0) (notificationEvents.size / (durationSeconds / 60.0)).coerceIn(0.0, 1.0) else 0.0

        // Burstiness: coefficient of variation of inter-event intervals
        var burstiness = 0.0
        if (eventTimestamps.size >= 3) {
            val intervals = (1 until eventTimestamps.size).map { (eventTimestamps[it] - eventTimestamps[it - 1]).toDouble() }
            val mean = intervals.average()
            if (mean > 0) {
                val variance = intervals.map { (it - mean) * (it - mean) }.average()
                val stdDev = sqrt(variance)
                val bRaw = (stdDev - mean) / (stdDev + mean)
                burstiness = ((bRaw + 1.0) / 2.0).coerceIn(0.0, 1.0)
            }
        }

        // Behavioral distraction score
        val behavioralDistractionScore = (0.3 * taskSwitchRate / 10.0 + 0.3 * notificationLoad + 0.2 * idleTimeRatio + 0.2 * burstiness).coerceIn(0.0, 1.0)

        // Focus hint (inverse of distraction)
        val focusHint = (1.0 - behavioralDistractionScore).coerceIn(0.0, 1.0)

        // Fragmented idle ratio: proportion of idle gaps that are short (< 5s)
        var fragmentedIdleCount = 0
        var totalIdleGapCount = 0
        for (i in 1 until eventTimestamps.size) {
            val gap = eventTimestamps[i] - eventTimestamps[i - 1]
            if (gap > 2000) {
                totalIdleGapCount++
                if (gap < 5000) fragmentedIdleCount++
            }
        }
        val fragmentedIdleRatio = if (totalIdleGapCount > 0) fragmentedIdleCount.toDouble() / totalIdleGapCount else 0.0

        // Scroll jitter rate
        val scrollJitterCount = scrollEvents.count { (it.metrics["direction_reversal"] as? Boolean) == true }
        val scrollJitterRate = if (scrollEvents.isNotEmpty()) scrollJitterCount.toDouble() / scrollEvents.size else 0.0

        // Deep focus blocks: continuous periods > 2 min with no idle > 5s
        val deepFocusBlocks = mutableListOf<Map<String, Any>>()
        if (eventTimestamps.size >= 2) {
            var blockStart = eventTimestamps.first()
            var prevTime = blockStart
            for (i in 1 until eventTimestamps.size) {
                val gap = eventTimestamps[i] - prevTime
                if (gap > 5000) {
                    // End of potential block
                    val blockDuration = prevTime - blockStart
                    if (blockDuration >= 120000) { // 2 minutes
                        deepFocusBlocks.add(mapOf(
                                "start_at" to isoFormatter.format(Instant.ofEpochMilli(blockStart)),
                                "end_at" to isoFormatter.format(Instant.ofEpochMilli(prevTime)),
                                "duration_ms" to blockDuration.toInt()
                        ))
                    }
                    blockStart = eventTimestamps[i]
                }
                prevTime = eventTimestamps[i]
            }
            // Check final block
            val blockDuration = prevTime - blockStart
            if (blockDuration >= 120000) {
                deepFocusBlocks.add(mapOf(
                        "start_at" to isoFormatter.format(Instant.ofEpochMilli(blockStart)),
                        "end_at" to isoFormatter.format(Instant.ofEpochMilli(prevTime)),
                        "duration_ms" to blockDuration.toInt()
                ))
            }
        }

        // Typing session summary
        val typingSessionSummary = computeTypingSessionSummary(typingEvents, durationSeconds)

        return mapOf(
                "interaction_intensity" to interactionIntensity,
                "task_switch_rate" to taskSwitchRate,
                "task_switch_cost" to taskSwitchCost,
                "idle_time_ratio" to idleTimeRatio,
                "active_time_ratio" to activeTimeRatio,
                "notification_load" to notificationLoad,
                "burstiness" to burstiness,
                "behavioral_distraction_score" to behavioralDistractionScore,
                "focus_hint" to focusHint,
                "fragmented_idle_ratio" to fragmentedIdleRatio,
                "scroll_jitter_rate" to scrollJitterRate,
                "deep_focus_blocks" to deepFocusBlocks,
                "typing_session_summary" to typingSessionSummary
        )
    }

    /**
     * Compute typing session summary from typing events.
     */
    private fun computeTypingSessionSummary(
            typingEvents: List<BehaviorEvent>,
            sessionDurationSeconds: Double
    ): Map<String, Any> {
        if (typingEvents.isEmpty()) {
            return mapOf(
                    "typing_session_count" to 0,
                    "average_keystrokes_per_session" to 0.0,
                    "average_typing_session_duration" to 0.0,
                    "average_typing_speed" to 0.0,
                    "average_typing_gap" to 0.0,
                    "average_inter_tap_interval" to 0.0,
                    "typing_cadence_stability" to 0.0,
                    "burstiness_of_typing" to 0.0,
                    "total_typing_duration" to 0,
                    "active_typing_ratio" to 0.0,
                    "typing_contribution_to_interaction_intensity" to 0.0,
                    "deep_typing_blocks" to 0,
                    "typing_fragmentation" to 0.0,
                    "clipboard_activity_rate" to 0.0,
                    "correction_rate" to 0.0,
                    "typing_metrics" to emptyList<Map<String, Any>>()
            )
        }

        val typingMetricsList = typingEvents.map { event ->
            val m = event.metrics
            mapOf(
                    "start_at" to ((m["start_at"] as? String) ?: ""),
                    "end_at" to ((m["end_at"] as? String) ?: ""),
                    "duration" to ((m["duration"] as? Number)?.toInt() ?: 0),
                    "deep_typing" to ((m["deep_typing"] as? Boolean) ?: false),
                    "typing_tap_count" to ((m["typing_tap_count"] as? Number)?.toInt() ?: 0),
                    "typing_speed" to ((m["typing_speed"] as? Number)?.toDouble() ?: 0.0),
                    "mean_inter_tap_interval_ms" to ((m["mean_inter_tap_interval_ms"] as? Number)?.toDouble() ?: 0.0),
                    "typing_cadence_variability" to ((m["typing_cadence_variability"] as? Number)?.toDouble() ?: 0.0),
                    "typing_cadence_stability" to ((m["typing_cadence_stability"] as? Number)?.toDouble() ?: 0.0),
                    "typing_gap_count" to ((m["typing_gap_count"] as? Number)?.toInt() ?: 0),
                    "typing_gap_ratio" to ((m["typing_gap_ratio"] as? Number)?.toDouble() ?: 0.0),
                    "typing_burstiness" to ((m["typing_burstiness"] as? Number)?.toDouble() ?: 0.0),
                    "typing_activity_ratio" to ((m["typing_activity_ratio"] as? Number)?.toDouble() ?: 0.0),
                    "typing_interaction_intensity" to ((m["typing_interaction_intensity"] as? Number)?.toDouble() ?: 0.0)
            )
        }

        val count = typingEvents.size
        val tapCounts = typingEvents.map { (it.metrics["typing_tap_count"] as? Number)?.toInt() ?: 0 }
        val durations = typingEvents.map { (it.metrics["duration"] as? Number)?.toInt() ?: 0 }
        val speeds = typingEvents.map { (it.metrics["typing_speed"] as? Number)?.toDouble() ?: 0.0 }
        val gapCounts = typingEvents.map { (it.metrics["typing_gap_count"] as? Number)?.toInt() ?: 0 }
        val interTapIntervals = typingEvents.map { (it.metrics["mean_inter_tap_interval_ms"] as? Number)?.toDouble() ?: 0.0 }
        val cadenceStabilities = typingEvents.map { (it.metrics["typing_cadence_stability"] as? Number)?.toDouble() ?: 0.0 }
        val burstinessValues = typingEvents.map { (it.metrics["typing_burstiness"] as? Number)?.toDouble() ?: 0.0 }
        val deepTypingCount = typingEvents.count { (it.metrics["deep_typing"] as? Boolean) == true }
        val interactionIntensities = typingEvents.map { (it.metrics["typing_interaction_intensity"] as? Number)?.toDouble() ?: 0.0 }

        val totalDuration = durations.sum()
        val avgKeystrokesPerSession = if (count > 0) tapCounts.average() else 0.0
        val avgDuration = if (count > 0) durations.average() else 0.0
        val avgSpeed = if (count > 0) speeds.average() else 0.0
        val avgGap = if (count > 0) gapCounts.average() else 0.0
        val avgInterTapInterval = if (count > 0) interTapIntervals.average() else 0.0
        val avgCadenceStability = if (count > 0) cadenceStabilities.average() else 0.0
        val avgBurstiness = if (count > 0) burstinessValues.average() else 0.0
        val activeTypingRatio = if (sessionDurationSeconds > 0) (totalDuration / sessionDurationSeconds).coerceIn(0.0, 1.0) else 0.0
        val avgInteractionIntensity = if (count > 0) interactionIntensities.average() else 0.0
        val typingContribution = avgInteractionIntensity * activeTypingRatio

        // Typing fragmentation: how spread out typing sessions are
        val typingFragmentation = if (count > 1 && sessionDurationSeconds > 0) {
            val avgSessionGap = (sessionDurationSeconds - totalDuration) / count
            (avgSessionGap / sessionDurationSeconds).coerceIn(0.0, 1.0)
        } else 0.0

        // Clipboard activity rate
        val totalTapCount = tapCounts.sum()
        val totalCopy = typingEvents.sumOf { (it.metrics["number_of_copy"] as? Number)?.toInt() ?: 0 }
        val totalPaste = typingEvents.sumOf { (it.metrics["number_of_paste"] as? Number)?.toInt() ?: 0 }
        val totalCut = typingEvents.sumOf { (it.metrics["number_of_cut"] as? Number)?.toInt() ?: 0 }
        val clipboardTotal = totalCopy + totalPaste + totalCut
        val clipboardActivityRate = if (totalTapCount + clipboardTotal > 0) clipboardTotal.toDouble() / (totalTapCount + clipboardTotal) else 0.0

        // Correction rate
        val totalBackspace = typingEvents.sumOf { (it.metrics["backspace_count"] as? Number)?.toInt() ?: 0 }
        val totalDelete = typingEvents.sumOf { (it.metrics["number_of_delete"] as? Number)?.toInt() ?: 0 }
        val correctionTotal = totalBackspace + totalDelete
        val correctionRate = if (totalTapCount + correctionTotal > 0) correctionTotal.toDouble() / (totalTapCount + correctionTotal) else 0.0

        return mapOf(
                "typing_session_count" to count,
                "average_keystrokes_per_session" to avgKeystrokesPerSession,
                "average_typing_session_duration" to avgDuration,
                "average_typing_speed" to avgSpeed,
                "average_typing_gap" to avgGap,
                "average_inter_tap_interval" to avgInterTapInterval,
                "typing_cadence_stability" to avgCadenceStability,
                "burstiness_of_typing" to avgBurstiness,
                "total_typing_duration" to totalDuration,
                "active_typing_ratio" to activeTypingRatio,
                "typing_contribution_to_interaction_intensity" to typingContribution,
                "deep_typing_blocks" to deepTypingCount,
                "typing_fragmentation" to typingFragmentation,
                "clipboard_activity_rate" to clipboardActivityRate,
                "correction_rate" to correctionRate,
                "typing_metrics" to typingMetricsList
        )
    }

    /**
     * Parse deep focus blocks from metrics.
     */
    private fun parseDeepFocusBlocks(blocks: Any?): List<DeepFocusBlock> {
        if (blocks == null) return emptyList()
        
        return when (blocks) {
            is List<*> -> {
                blocks.mapNotNull { block ->
                    when (block) {
                        is Map<*, *> -> {
                            DeepFocusBlock(
                                    startAt = (block["start_at"] as? String) ?: "",
                                    endAt = (block["end_at"] as? String) ?: "",
                                    durationMs = ((block["duration_ms"] as? Number)?.toInt()) ?: 0
                            )
                        }
                        else -> null
                    }
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Convert typing summary map to TypingSessionSummary object.
     */
    private fun convertTypingSummaryToObject(typingSummary: Map<String, Any>): TypingSessionSummary {
        // Extract typing metrics list
        val typingMetricsList = (typingSummary["typing_metrics"] as? List<*>)?.mapNotNull { metric ->
            when (metric) {
                is Map<*, *> -> {
                    TypingMetrics(
                            startAt = (metric["start_at"] as? String) ?: "",
                            endAt = (metric["end_at"] as? String) ?: "",
                            duration = ((metric["duration"] as? Number)?.toInt()) ?: 0,
                            deepTyping = (metric["deep_typing"] as? Boolean) ?: false,
                            typingTapCount = ((metric["typing_tap_count"] as? Number)?.toInt()) ?: 0,
                            typingSpeed = ((metric["typing_speed"] as? Number)?.toDouble()) ?: 0.0,
                            meanInterTapIntervalMs = ((metric["mean_inter_tap_interval_ms"] as? Number)?.toDouble()) ?: 0.0,
                            typingCadenceVariability = ((metric["typing_cadence_variability"] as? Number)?.toDouble()) ?: 0.0,
                            typingCadenceStability = ((metric["typing_cadence_stability"] as? Number)?.toDouble()) ?: 0.0,
                            typingGapCount = ((metric["typing_gap_count"] as? Number)?.toInt()) ?: 0,
                            typingGapRatio = ((metric["typing_gap_ratio"] as? Number)?.toDouble()) ?: 0.0,
                            typingBurstiness = ((metric["typing_burstiness"] as? Number)?.toDouble()) ?: 0.0,
                            typingActivityRatio = ((metric["typing_activity_ratio"] as? Number)?.toDouble()) ?: 0.0,
                            typingInteractionIntensity = ((metric["typing_interaction_intensity"] as? Number)?.toDouble()) ?: 0.0
                    )
                }
                else -> null
            }
        } ?: emptyList()

        return TypingSessionSummary(
                typingSessionCount = ((typingSummary["typing_session_count"] as? Number)?.toInt()) ?: 0,
                averageKeystrokesPerSession = ((typingSummary["average_keystrokes_per_session"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingSessionDuration = ((typingSummary["average_typing_session_duration"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingSpeed = ((typingSummary["average_typing_speed"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingGap = ((typingSummary["average_typing_gap"] as? Number)?.toDouble()) ?: 0.0,
                averageInterTapInterval = ((typingSummary["average_inter_tap_interval"] as? Number)?.toDouble()) ?: 0.0,
                typingCadenceStability = ((typingSummary["typing_cadence_stability"] as? Number)?.toDouble()) ?: 0.0,
                burstinessOfTyping = ((typingSummary["burstiness_of_typing"] as? Number)?.toDouble()) ?: 0.0,
                totalTypingDuration = ((typingSummary["total_typing_duration"] as? Number)?.toInt()) ?: 0,
                activeTypingRatio = ((typingSummary["active_typing_ratio"] as? Number)?.toDouble()) ?: 0.0,
                typingContributionToInteractionIntensity = ((typingSummary["typing_contribution_to_interaction_intensity"] as? Number)?.toDouble()) ?: 0.0,
                deepTypingBlocks = ((typingSummary["deep_typing_blocks"] as? Number)?.toInt()) ?: 0,
                typingFragmentation = ((typingSummary["typing_fragmentation"] as? Number)?.toDouble()) ?: 0.0,
                clipboardActivityRate = ((typingSummary["clipboard_activity_rate"] as? Number)?.toDouble()) ?: 0.0,
                correctionRate = ((typingSummary["correction_rate"] as? Number)?.toDouble()) ?: 0.0,
                individualTypingSessions = typingMetricsList
        )
    }

    // Behavioral metrics are computed locally in computeBehavioralMetrics().

    private fun computeNotificationClusteringIndex(
            notificationEvents: List<BehaviorEvent>
    ): Double {
        if (notificationEvents.size < 2) return 0.0

        val intervals = mutableListOf<Long>()
        for (i in 1 until notificationEvents.size) {
            try {
                val prevTime = Instant.parse(notificationEvents[i - 1].timestamp).toEpochMilli()
                val currTime = Instant.parse(notificationEvents[i].timestamp).toEpochMilli()
                intervals.add(currTime - prevTime)
            } catch (e: Exception) {
                // Skip invalid timestamps
            }
        }

        if (intervals.isEmpty()) return 0.0

        val mean = intervals.average()
        if (mean == 0.0) return 0.0

        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0

        // Clustering index: 1 - normalized CV
        return (1.0 - (cv / 10.0).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }

    private fun getScreenBrightness(): Float {
        return try {
            val brightness =
                    Settings.System.getInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS
                    )
            brightness / 255f // Normalize to 0.0-1.0
        } catch (e: Exception) {
            0.5f // Default
        }
    }

    private fun isInternetConnected(): Boolean {
        return try {
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities =
                        connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION") val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isDoNotDisturbEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                android.app.NotificationManager
                val filter = notificationManager.currentInterruptionFilter
                filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
                        filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                        filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    fun getStartTimestamp(): Long = startTimestamp

    /**
     * Calculate metrics for a specific time range within this session. This method filters events
     * by the time range and calculates behavioral metrics dynamically.
     */
    @Synchronized
    fun calculateMetricsForTimeRange(
            startTimestampSeconds: Int,
            endTimestampSeconds: Int
    ): Map<String, Any?> {
        val startTimestampMs = startTimestampSeconds * 1000L
        val endTimestampMs = endTimestampSeconds * 1000L

        // Validate time range is within session duration (with 1 second tolerance)
        val toleranceMs = 1000L // 1 second tolerance
        if (startTimestampMs < (startTimestamp - toleranceMs) ||
                        endTimestampMs > (System.currentTimeMillis() + toleranceMs)
        ) {
            throw IllegalArgumentException(
                    "Time range [$startTimestampMs, $endTimestampMs] is out of session bounds " +
                            "[$startTimestamp, ${System.currentTimeMillis()}]. " +
                            "Session duration: ${System.currentTimeMillis() - startTimestamp}ms. " +
                            "Allowed tolerance: ${toleranceMs}ms"
            )
        }

        // Filter events by time range
        val filteredEvents =
                allEvents.filter { event ->
                    try {
                        val eventTime = Instant.parse(event.timestamp).toEpochMilli()
                        eventTime >= startTimestampMs && eventTime <= endTimestampMs
                    } catch (e: Exception) {
                        false // Skip invalid timestamps
                    }
                }

        // Calculate duration
        val duration = endTimestampMs - startTimestampMs
        val durationSeconds = duration / 1000.0

        // Compute notification summary
        val notificationEvents =
                filteredEvents.filter { it.eventType == BehaviorEventType.NOTIFICATION }
        val notificationCount = notificationEvents.size
        val notificationIgnored =
                notificationEvents.count { (it.metrics["action"] as? String) == "ignored" }
        val notificationIgnoreRate =
                if (notificationCount > 0) {
                    notificationIgnored.toDouble() / notificationCount
                } else 0.0
        val notificationClusteringIndex = computeNotificationClusteringIndex(notificationEvents)

        // Compute call summary
        val callEvents = filteredEvents.filter { it.eventType == BehaviorEventType.CALL }
        val callCount = callEvents.size
        val callIgnored = callEvents.count { (it.metrics["action"] as? String) == "ignored" }

        // Compute behavioral metrics locally
        val rangeMetrics = computeBehavioralMetrics(
                filteredEvents,
                duration,
                startTimestampMs,
                endTimestampMs
        )

        // Extract behavioral metrics from results
        val behavioralMetricsMap = rangeMetrics.filterKeys { key ->
            key != "typing_session_summary" // Separate typing summary
        }
        
        // Extract typing session summary from results
        val typingSummaryForRange = rangeMetrics["typing_session_summary"] as? Map<String, Any>
        val typingSessionSummary = if (typingSummaryForRange != null && typingSummaryForRange.isNotEmpty()) {
            typingSummaryForRange
        } else {
            mapOf(
                    "typing_session_count" to 0,
                    "average_keystrokes_per_session" to 0.0,
                    "average_typing_session_duration" to 0.0,
                    "average_typing_speed" to 0.0,
                    "average_typing_gap" to 0.0,
                    "average_inter_tap_interval" to 0.0,
                    "typing_cadence_stability" to 0.0,
                    "burstiness_of_typing" to 0.0,
                    "total_typing_duration" to 0,
                    "active_typing_ratio" to 0.0,
                    "typing_contribution_to_interaction_intensity" to 0.0,
                    "deep_typing_blocks" to 0,
                    "typing_fragmentation" to 0.0,
                    "typing_metrics" to emptyList<Map<String, Any>>()
            )
        }

        // Get current device context and system state
        val currentScreenBrightness = getScreenBrightness()
        val currentOrientation = context.resources.configuration.orientation
        val orientationStr =
                when (currentOrientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> "landscape"
                    else -> "portrait"
                }

        // Build and return metrics map
        return mapOf(
                "behavioral_metrics" to behavioralMetricsMap,
                "device_context" to
                        mapOf(
                                "avg_screen_brightness" to currentScreenBrightness.toDouble(),
                                "start_orientation" to orientationStr,
                                "orientation_changes" to orientationChangeCount
                        ),
                "system_state" to
                        mapOf(
                                "internet_state" to isInternetConnected(),
                                "do_not_disturb" to isDoNotDisturbEnabled(),
                                "charging" to isCharging()
                        ),
                "activity_summary" to
                        mapOf(
                                "total_events" to filteredEvents.size,
                                "app_switch_count" to appSwitchCount
                        ),
                "notification_summary" to
                        mapOf(
                                "notification_count" to notificationCount,
                                "notification_ignored" to notificationIgnored,
                                "notification_ignore_rate" to notificationIgnoreRate,
                                "notification_clustering_index" to notificationClusteringIndex,
                                "call_count" to callCount,
                                "call_ignored" to callIgnored
                        ),
                "typing_session_summary" to typingSessionSummary,
                "motion_data" to
                        emptyList<
                                Map<String, Any>>() // Placeholder - motion data not yet implemented
        )
    }

    // Typing session summary is computed locally in computeTypingSessionSummary().
}
