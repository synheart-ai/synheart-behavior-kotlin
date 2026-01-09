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
import kotlin.math.exp
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

        // Compute behavioral metrics from events (matching Flutter SDK formulas)
        val behavioralMetrics =
                computeBehavioralMetrics(
                        events,
                        duration,
                        startTimestamp,
                        endTimestamp,
                        notificationCount,
                        callCount
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

        // Compute typing session summary
        val typingSessionSummary = computeTypingSessionSummaryObject(events, duration)

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

    // Compute behavioral metrics matching Flutter SDK's computeBehavioralMetrics
    private fun computeBehavioralMetrics(
            events: List<BehaviorEvent>,
            durationMs: Long,
            sessionStartTime: Long,
            sessionEndTime: Long,
            notificationCount: Int,
            callCount: Int
    ): BehavioralMetrics {
        val durationSeconds = durationMs / 1000.0

        // Step 1: Compute burstiness
        val burstiness = computeBurstiness(events)

        // Step 2: Compute notification_load = 1 - exp(-notification_rate / λ)
        val notificationRate =
                if (durationSeconds > 0) {
                    notificationCount / durationSeconds
                } else 0.0
        val lambda = 1.0 / 60.0
        val notificationLoad =
                if (notificationRate > 0) {
                    1.0 - exp(-notificationRate / lambda)
                } else 0.0

        // Step 3: Compute task_switch_rate
        val taskSwitchRateRaw =
                if (durationSeconds > 0) {
                    appSwitchCount / durationSeconds
                } else 0.0
        val mu = 1.0 / 30.0
        val taskSwitchRate =
                if (taskSwitchRateRaw > 0) {
                    1.0 - exp(-taskSwitchRateRaw / mu)
                } else 0.0

        // Step 4: Compute task_switch_cost
        val taskSwitchCost =
                if (appSwitchCount > 0) {
                    (durationMs / appSwitchCount).toInt().coerceIn(0, 10000)
                } else 0

        // Step 5: Compute idle_ratio
        val idleRatio = computeIdleRatio(events, durationMs)

        // Step 6: Compute active_time_ratio
        val totalIdleTimeMs = (idleRatio * durationMs).toLong()
        val activeInteractionTimeMs = durationMs - totalIdleTimeMs - taskSwitchCost
        val activeTimeRatio =
                if (durationMs > 0) {
                    (activeInteractionTimeMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
                } else 0.0

        // Step 7: Compute fragmented_idle_ratio
        val fragmentedIdleRatio = computeFragmentedIdleRatio(events, durationMs)

        // Step 8: Compute scroll_jitter_rate
        val scrollJitterRate = computeScrollJitterRate(events)

        // Step 9: Compute distraction_score
        val w1 = 0.35
        val w2 = 0.30
        val w3 = 0.20
        val w4 = 0.15
        val behavioralDistractionScore =
                (w1 * taskSwitchRate +
                                w2 * notificationLoad +
                                w3 * fragmentedIdleRatio +
                                w4 * scrollJitterRate)
                        .coerceIn(0.0, 1.0)

        // Step 10: Compute focus_hint
        val focusHint = 1.0 - behavioralDistractionScore

        // Step 11: Compute interaction_intensity = [total events except interruptions and typing +
        // (Typing durations/10s)] / session_duration
        // Interruptions = notifications, calls, app switches
        // Typing events are handled separately: we add (total_typing_duration_seconds / 10) instead
        // of counting typing events
        val interruptionCount = notificationCount + callCount + appSwitchCount

        // Count typing events to exclude them from event count
        val typingEvents = events.filter { it.eventType == BehaviorEventType.TYPING }
        val typingEventCount = typingEvents.size

        // Calculate total typing duration in seconds (sum of all typing session durations)
        val totalTypingDurationSeconds =
                if (typingEvents.isNotEmpty()) {
                    typingEvents
                            .mapNotNull { event -> (event.metrics["duration"] as? Number)?.toInt() }
                            .sum()
                            .toDouble()
                } else {
                    0.0
                }

        // Total events excluding interruptions and typing events
        val totalEventsExceptInterruptionsAndTyping =
                events.size - interruptionCount - typingEventCount

        // Interaction intensity = [non-interruption non-typing events + (typing_duration/10)] /
        // session_duration
        val interactionIntensity =
                if (durationSeconds > 0) {
                    val typingContribution = totalTypingDurationSeconds / 10.0
                    ((totalEventsExceptInterruptionsAndTyping + typingContribution) /
                                    durationSeconds)
                            .coerceIn(0.0, Double.MAX_VALUE)
                } else 0.0

        // Step 12: Compute deep_focus_blocks
        val deepFocusBlocks =
                computeDeepFocusBlocks(
                        events,
                        durationMs,
                        sessionStartTime,
                        sessionEndTime,
                        notificationCount,
                        callCount,
                        appSwitchCount
                )

        return BehavioralMetrics(
                interactionIntensity = interactionIntensity,
                taskSwitchRate = taskSwitchRate,
                taskSwitchCost = taskSwitchCost,
                idleTimeRatio = idleRatio,
                activeTimeRatio = activeTimeRatio,
                notificationLoad = notificationLoad,
                burstiness = burstiness,
                behavioralDistractionScore = behavioralDistractionScore,
                focusHint = focusHint,
                fragmentedIdleRatio = fragmentedIdleRatio,
                scrollJitterRate = scrollJitterRate,
                deepFocusBlocks = deepFocusBlocks
        )
    }

    private fun computeBurstiness(events: List<BehaviorEvent>): Double {
        if (events.size < 2) return 0.0

        val intervals = mutableListOf<Long>()
        for (i in 1 until events.size) {
            try {
                val prevTime = Instant.parse(events[i - 1].timestamp).toEpochMilli()
                val currTime = Instant.parse(events[i].timestamp).toEpochMilli()
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
        if (stdDev == 0.0) return 0.0

        val burstinessRaw = (stdDev - mean) / (stdDev + mean)
        return ((burstinessRaw + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun computeIdleRatio(events: List<BehaviorEvent>, durationMs: Long): Double {
        if (events.size < 2) return 0.0

        val idleThresholdMs = 30000L // 30 seconds
        var totalIdleTime = 0L
        for (i in 1 until events.size) {
            try {
                val prevTime = Instant.parse(events[i - 1].timestamp).toEpochMilli()
                val currTime = Instant.parse(events[i].timestamp).toEpochMilli()
                val gap = currTime - prevTime
                if (gap > idleThresholdMs) {
                    totalIdleTime += gap - idleThresholdMs
                }
            } catch (e: Exception) {
                // Skip invalid timestamps
            }
        }

        return if (durationMs > 0) {
            (totalIdleTime.toDouble() / durationMs).coerceIn(0.0, 1.0)
        } else 0.0
    }

    private fun computeFragmentedIdleRatio(events: List<BehaviorEvent>, durationMs: Long): Double {
        if (events.size < 2) return 0.0

        val idleThresholdMs = 30000L // 30 seconds
        var numberOfIdleSegments = 0
        for (i in 1 until events.size) {
            try {
                val prevTime = Instant.parse(events[i - 1].timestamp).toEpochMilli()
                val currTime = Instant.parse(events[i].timestamp).toEpochMilli()
                val gap = currTime - prevTime
                if (gap > idleThresholdMs) {
                    numberOfIdleSegments++
                }
            } catch (e: Exception) {
                // Skip invalid timestamps
            }
        }

        val durationSeconds = durationMs / 1000.0
        return if (durationSeconds > 0) {
            (numberOfIdleSegments / durationSeconds).coerceIn(0.0, Double.MAX_VALUE)
        } else 0.0
    }

    private fun computeScrollJitterRate(events: List<BehaviorEvent>): Double {
        val scrollEvents = events.filter { it.eventType == BehaviorEventType.SCROLL }
        if (scrollEvents.size < 2) return 0.0

        var directionReversals = 0
        var previousDirection: String? = null
        for (event in scrollEvents) {
            val currentDirection = event.metrics["direction"] as? String
            if (currentDirection != null &&
                            previousDirection != null &&
                            currentDirection != previousDirection
            ) {
                directionReversals++
            }
            previousDirection = currentDirection
        }

        val totalScrollEvents = scrollEvents.size
        return if (totalScrollEvents > 1) {
            (directionReversals.toDouble() / (totalScrollEvents - 1)).coerceIn(0.0, 1.0)
        } else 0.0
    }

    private fun computeDeepFocusBlocks(
            events: List<BehaviorEvent>,
            durationMs: Long, // Unused but kept for API consistency
            sessionStartTime: Long,
            sessionEndTime: Long,
            notificationCount: Int, // Unused but kept for API consistency
            callCount: Int, // Unused but kept for API consistency
            appSwitchCount: Int // Unused but kept for API consistency
    ): List<DeepFocusBlock> {
        // Deep focus block = continuous app engagement ≥ 120s without
        // idle, app switch, notification or call event
        val deepFocusBlocks = mutableListOf<DeepFocusBlock>()
        val minBlockDurationMs = 120000L // 120 seconds

        if (events.size < 2) return deepFocusBlocks

        val idleThresholdMs = 30000L // 30 seconds
        var blockStart: Long? = null
        var blockEnd: Long? = null
        var lastBlockEndTime: Long? = null // Track when last block ended

        // Filter out interruption events (notifications, calls, app switches)
        // Note: app_switch is not an event type, but we track app switches separately
        // For now, we only filter notification and call events
        val interruptionEventTypes = setOf(BehaviorEventType.NOTIFICATION, BehaviorEventType.CALL)

        for (i in events.indices) {
            try {
                val event = events[i]
                val currTime = Instant.parse(event.timestamp).toEpochMilli()

                // Check if this is an interruption event
                val isInterruption = interruptionEventTypes.contains(event.eventType)

                // Check gap from previous event
                val gap =
                        if (i > 0) {
                            val prevTime = Instant.parse(events[i - 1].timestamp).toEpochMilli()
                            currTime - prevTime
                        } else {
                            // First event - check gap from session start
                            currTime - sessionStartTime
                        }

                // If we hit an interruption or idle gap, end current block
                if (isInterruption || gap > idleThresholdMs) {
                    if (blockStart != null && blockEnd != null) {
                        val blockDuration = blockEnd - blockStart
                        if (blockDuration >= minBlockDurationMs) {
                            deepFocusBlocks.add(
                                    DeepFocusBlock(
                                            startAt = Instant.ofEpochMilli(blockStart).toString(),
                                            endAt = Instant.ofEpochMilli(blockEnd).toString(),
                                            durationMs = blockDuration.toInt()
                                    )
                            )
                        }
                    }
                    lastBlockEndTime = if (isInterruption) currTime else blockEnd
                    blockStart = null
                    blockEnd = null
                } else {
                    // Continue or start a focus block
                    if (blockStart == null) {
                        // Starting a new block - check if we should start from session start or
                        // previous block end
                        if (i == 0 && gap <= idleThresholdMs) {
                            // First event and close to session start - start from session start
                            blockStart = sessionStartTime
                        } else if (lastBlockEndTime != null &&
                                        (currTime - lastBlockEndTime) <= idleThresholdMs
                        ) {
                            // Close to previous block end - start from previous block end
                            blockStart = lastBlockEndTime
                        } else {
                            // Start from current event time
                            blockStart = currTime
                        }
                    }
                    blockEnd = currTime
                }
            } catch (e: Exception) {
                // Skip invalid timestamps
            }
        }

        // Check final block - include time from last event to session end if recent
        if (blockStart != null && blockEnd != null) {
            // Get the last event time in the block
            val lastEventTime = blockEnd

            // If last event was recent (within idle threshold of session end), extend to session
            // end
            // This ensures we count engagement time even if no events were generated at the end
            val timeFromLastEventToSessionEnd = sessionEndTime - lastEventTime
            val finalBlockEnd =
                    if (timeFromLastEventToSessionEnd <= idleThresholdMs) {
                        // Last event was recent, include time up to session end
                        sessionEndTime
                    } else {
                        // Last event was too long ago, use event timestamp
                        blockEnd
                    }

            val blockDuration = finalBlockEnd - blockStart
            if (blockDuration >= minBlockDurationMs) {
                deepFocusBlocks.add(
                        DeepFocusBlock(
                                startAt = Instant.ofEpochMilli(blockStart).toString(),
                                endAt = Instant.ofEpochMilli(finalBlockEnd).toString(),
                                durationMs = blockDuration.toInt()
                        )
                )
            }
        }

        return deepFocusBlocks
    }

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

        // Compute behavioral metrics
        val behavioralMetrics =
                computeBehavioralMetrics(
                        filteredEvents,
                        duration,
                        startTimestampMs,
                        endTimestampMs,
                        notificationCount,
                        callCount
                )

        // Compute typing session summary (placeholder for now - typing not fully implemented)
        val typingSessionSummary = computeTypingSessionSummary(filteredEvents, duration)

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
                "behavioral_metrics" to
                        mapOf(
                                "interaction_intensity" to behavioralMetrics.interactionIntensity,
                                "task_switch_rate" to behavioralMetrics.taskSwitchRate,
                                "task_switch_cost" to behavioralMetrics.taskSwitchCost,
                                "idle_time_ratio" to behavioralMetrics.idleTimeRatio,
                                "active_time_ratio" to behavioralMetrics.activeTimeRatio,
                                "notification_load" to behavioralMetrics.notificationLoad,
                                "burstiness" to behavioralMetrics.burstiness,
                                "behavioral_distraction_score" to
                                        behavioralMetrics.behavioralDistractionScore,
                                "focus_hint" to behavioralMetrics.focusHint,
                                "fragmented_idle_ratio" to behavioralMetrics.fragmentedIdleRatio,
                                "scroll_jitter_rate" to behavioralMetrics.scrollJitterRate,
                                "deep_focus_blocks" to
                                        behavioralMetrics.deepFocusBlocks.map { block ->
                                            mapOf(
                                                    "start_at" to block.startAt,
                                                    "end_at" to block.endAt,
                                                    "duration_ms" to block.durationMs
                                            )
                                        }
                        ),
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

    /** Compute typing session summary as TypingSessionSummary object. */
    private fun computeTypingSessionSummaryObject(
            events: List<BehaviorEvent>,
            durationMs: Long
    ): TypingSessionSummary? {
        // Extract all typing events
        val typingEvents = events.filter { it.eventType == BehaviorEventType.TYPING }

        if (typingEvents.isEmpty()) {
            return null
        }

        // Each typing event represents one typing session
        val typingSessionCount = typingEvents.size

        // Extract metrics from each typing event
        val sessionMetrics =
                typingEvents.map { event ->
                    TypingMetrics(
                            startAt = (event.metrics["start_at"] as? String) ?: "",
                            endAt = (event.metrics["end_at"] as? String) ?: "",
                            duration = ((event.metrics["duration"] as? Number)?.toInt() ?: 0),
                            deepTyping = (event.metrics["deep_typing"] as? Boolean) ?: false,
                            typingTapCount =
                                    ((event.metrics["typing_tap_count"] as? Number)?.toInt() ?: 0),
                            typingSpeed = ((event.metrics["typing_speed"] as? Number)?.toDouble()
                                            ?: 0.0),
                            meanInterTapIntervalMs =
                                    ((event.metrics["mean_inter_tap_interval_ms"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            typingCadenceVariability =
                                    ((event.metrics["typing_cadence_variability"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            typingCadenceStability =
                                    ((event.metrics["typing_cadence_stability"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            typingGapCount =
                                    ((event.metrics["typing_gap_count"] as? Number)?.toInt() ?: 0),
                            typingGapRatio =
                                    ((event.metrics["typing_gap_ratio"] as? Number)?.toDouble()
                                            ?: 0.0),
                            typingBurstiness =
                                    ((event.metrics["typing_burstiness"] as? Number)?.toDouble()
                                            ?: 0.0),
                            typingActivityRatio =
                                    ((event.metrics["typing_activity_ratio"] as? Number)?.toDouble()
                                            ?: 0.0),
                            typingInteractionIntensity =
                                    ((event.metrics["typing_interaction_intensity"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0)
                    )
                }

        val averageKeystrokesPerSession = sessionMetrics.map { it.typingTapCount }.average()
        val averageTypingSessionDuration = sessionMetrics.map { it.duration }.average()
        val averageTypingSpeed = sessionMetrics.map { it.typingSpeed }.average()
        val averageTypingGap = sessionMetrics.map { it.meanInterTapIntervalMs }.average()
        val averageInterTapInterval = sessionMetrics.map { it.meanInterTapIntervalMs }.average()
        val typingCadenceStability = sessionMetrics.map { it.typingCadenceStability }.average()
        val burstinessOfTyping = sessionMetrics.map { it.typingBurstiness }.average()
        val totalTypingDuration = sessionMetrics.map { it.duration }.sum()
        val activeTypingRatio =
                if (durationMs > 0) {
                    (totalTypingDuration * 1000.0 / durationMs).coerceIn(0.0, 1.0)
                } else 0.0
        val typingContributionToInteractionIntensity =
                if (events.isNotEmpty()) {
                    typingEvents.size.toDouble() / events.size
                } else 0.0
        val deepTypingBlocks = sessionMetrics.count { it.deepTyping }
        val typingFragmentation = sessionMetrics.map { it.typingGapRatio }.average()

        return TypingSessionSummary(
                typingSessionCount = typingSessionCount,
                averageKeystrokesPerSession = averageKeystrokesPerSession,
                averageTypingSessionDuration = averageTypingSessionDuration,
                averageTypingSpeed = averageTypingSpeed,
                averageTypingGap = averageTypingGap,
                averageInterTapInterval = averageInterTapInterval,
                typingCadenceStability = typingCadenceStability,
                burstinessOfTyping = burstinessOfTyping,
                totalTypingDuration = totalTypingDuration,
                activeTypingRatio = activeTypingRatio,
                typingContributionToInteractionIntensity = typingContributionToInteractionIntensity,
                deepTypingBlocks = deepTypingBlocks,
                typingFragmentation = typingFragmentation,
                individualTypingSessions = sessionMetrics
        )
    }

    private fun computeTypingSessionSummary(
            events: List<BehaviorEvent>,
            durationMs: Long
    ): Map<String, Any> {
        // Extract all typing events
        val typingEvents = events.filter { it.eventType == BehaviorEventType.TYPING }

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
                    "typing_metrics" to emptyList<Map<String, Any>>()
            )
        }

        // Each typing event represents one typing session
        val typingSessionCount = typingEvents.size

        // Extract metrics from each typing event
        val sessionMetrics =
                typingEvents.map { event ->
                    mapOf(
                            "typing_tap_count" to
                                    ((event.metrics["typing_tap_count"] as? Number)?.toInt() ?: 0),
                            "typing_speed" to
                                    ((event.metrics["typing_speed"] as? Number)?.toDouble() ?: 0.0),
                            "mean_inter_tap_interval_ms" to
                                    ((event.metrics["mean_inter_tap_interval_ms"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            "typing_cadence_stability" to
                                    ((event.metrics["typing_cadence_stability"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            "typing_gap_ratio" to
                                    ((event.metrics["typing_gap_ratio"] as? Number)?.toDouble()
                                            ?: 0.0),
                            "typing_burstiness" to
                                    ((event.metrics["typing_burstiness"] as? Number)?.toDouble()
                                            ?: 0.0),
                            "duration" to ((event.metrics["duration"] as? Number)?.toInt() ?: 0),
                            "deep_typing" to (event.metrics["deep_typing"] as? Boolean ?: false)
                    )
                }

        val averageKeystrokesPerSession =
                sessionMetrics.map { it["typing_tap_count"] as Int }.average()
        val averageTypingSessionDuration = sessionMetrics.map { it["duration"] as Int }.average()
        val averageTypingSpeed = sessionMetrics.map { it["typing_speed"] as Double }.average()
        val averageTypingGap =
                sessionMetrics.map { it["mean_inter_tap_interval_ms"] as Double }.average()
        val averageInterTapInterval =
                sessionMetrics.map { it["mean_inter_tap_interval_ms"] as Double }.average()
        val typingCadenceStability =
                sessionMetrics.map { it["typing_cadence_stability"] as Double }.average()
        val burstinessOfTyping = sessionMetrics.map { it["typing_burstiness"] as Double }.average()
        val totalTypingDuration = sessionMetrics.map { it["duration"] as Int }.sum()
        val activeTypingRatio =
                if (durationMs > 0) {
                    (totalTypingDuration * 1000.0 / durationMs).coerceIn(0.0, 1.0)
                } else 0.0
        val typingContributionToInteractionIntensity =
                if (events.isNotEmpty()) {
                    typingEvents.size.toDouble() / events.size
                } else 0.0
        val deepTypingBlocks = sessionMetrics.count { it["deep_typing"] as Boolean }
        val typingFragmentation = sessionMetrics.map { it["typing_gap_ratio"] as Double }.average()

        // Individual typing session metrics
        val individualMetrics =
                typingEvents.map { event ->
                    mapOf(
                            "start_at" to (event.metrics["start_at"] as? String ?: ""),
                            "end_at" to (event.metrics["end_at"] as? String ?: ""),
                            "deep_typing" to (event.metrics["deep_typing"] as? Boolean ?: false),
                            "typing_tap_count" to
                                    ((event.metrics["typing_tap_count"] as? Number)?.toInt() ?: 0),
                            "typing_speed" to
                                    ((event.metrics["typing_speed"] as? Number)?.toDouble() ?: 0.0),
                            "mean_inter_tap_interval_ms" to
                                    ((event.metrics["mean_inter_tap_interval_ms"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            "typing_cadence_variability" to
                                    ((event.metrics["typing_cadence_variability"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            "typing_cadence_stability" to
                                    ((event.metrics["typing_cadence_stability"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0),
                            "typing_gap_count" to
                                    ((event.metrics["typing_gap_count"] as? Number)?.toInt() ?: 0),
                            "typing_gap_ratio" to
                                    ((event.metrics["typing_gap_ratio"] as? Number)?.toDouble()
                                            ?: 0.0),
                            "typing_burstiness" to
                                    ((event.metrics["typing_burstiness"] as? Number)?.toDouble()
                                            ?: 0.0),
                            "typing_activity_ratio" to
                                    ((event.metrics["typing_activity_ratio"] as? Number)?.toDouble()
                                            ?: 0.0),
                            "typing_interaction_intensity" to
                                    ((event.metrics["typing_interaction_intensity"] as? Number)
                                            ?.toDouble()
                                            ?: 0.0)
                    )
                }

        return mapOf(
                "typing_session_count" to typingSessionCount,
                "average_keystrokes_per_session" to averageKeystrokesPerSession,
                "average_typing_session_duration" to averageTypingSessionDuration,
                "average_typing_speed" to averageTypingSpeed,
                "average_typing_gap" to averageTypingGap,
                "average_inter_tap_interval" to averageInterTapInterval,
                "typing_cadence_stability" to typingCadenceStability,
                "burstiness_of_typing" to burstinessOfTyping,
                "total_typing_duration" to totalTypingDuration,
                "active_typing_ratio" to activeTypingRatio,
                "typing_contribution_to_interaction_intensity" to
                        typingContributionToInteractionIntensity,
                "deep_typing_blocks" to deepTypingBlocks,
                "typing_fragmentation" to typingFragmentation,
                "typing_metrics" to individualMetrics
        )
    }
}
