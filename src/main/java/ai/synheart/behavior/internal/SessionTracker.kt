package ai.synheart.behavior.internal

import ai.synheart.behavior.*
import ai.synheart.behavior.TypingMetrics
import ai.synheart.behavior.TypingSessionSummary
import ai.synheart.behavior.extractBehavioralMetricsFromHsi
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
import java.util.TimeZone
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

        // Compute behavioral metrics using Flux (Rust) - required
        val (fluxMetrics, performanceInfo) = computeBehavioralMetricsWithFlux(
                events,
                duration,
                startTimestamp,
                endTimestamp
        )
        
        // Use flux metrics (or default when Flux was unavailable)
        val metrics = fluxMetrics ?: defaultFluxMetrics()

        // Convert Flux metrics map to BehavioralMetrics object
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

        // Extract typing session summary from Flux metrics (primary source)
        val fluxTypingSummary = metrics["typing_session_summary"] as? Map<String, Any>
        val typingSessionSummary = if (fluxTypingSummary != null && fluxTypingSummary.isNotEmpty()) {
            // Convert Flux typing summary map to TypingSessionSummary object
            convertFluxTypingSummaryToObject(fluxTypingSummary)
        } else {
            // Fallback to empty summary if Flux typing summary not available
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

        // Performance info is available from computeBehavioralMetricsWithFlux
        // Note: PerformanceInfo is not part of BehaviorSessionSummary in Kotlin SDK
        
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

    /**
     * End the current session and return HSI-compliant output using synheart-flux.
     *
     * If synheart-flux is not available, returns null. Use `getSessionSummary` as fallback.
     *
     * @param fluxProcessor Optional stateful processor with baselines (if null, uses stateless)
     */
    @Synchronized
    fun getSessionSummaryWithHsi(
            inputSummary: Map<String, Any?>,
            attentionSummary: Map<String, Any?>,
            gestureSummary: Map<String, Any?>,
            motionData: List<MotionDataPoint>? = null,
            fluxProcessor: FluxBehaviorProcessor? = null
    ): HsiBehaviorPayload? {
        if (!FluxBridge.isAvailable()) {
            android.util.Log.d(TAG, "synheart-flux not available for HSI output")
            return null
        }

        val endTimestamp = System.currentTimeMillis()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "android-device"
        val timezone = TimeZone.getDefault().id

        // Convert events to Flux JSON format
        val events = allEvents.toList()
        val fluxJson = convertEventsToFluxJson(
                sessionId = sessionId,
                deviceId = deviceId,
                timezone = timezone,
                startTimeMs = startTimestamp,
                endTimeMs = endTimestamp,
                events = events
        )

        // Compute HSI metrics using Rust
        var hsiPayload: HsiBehaviorPayload? = null

        if (fluxProcessor != null) {
            // Use stateful processor with baselines
            try {
                val hsiJson = fluxProcessor.process(fluxJson)
                hsiPayload = parseHsiJson(hsiJson)
                android.util.Log.d(TAG, "Successfully computed HSI metrics using synheart-flux (stateful)")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Stateful processing failed: ${e.message}")
            }
        }

        // Fallback to stateless if stateful failed
        if (hsiPayload == null) {
            val hsiJson = FluxBridge.behaviorToHsi(fluxJson)
            if (hsiJson != null) {
                hsiPayload = parseHsiJson(hsiJson)
                android.util.Log.d(TAG, "Successfully computed HSI metrics using synheart-flux (stateless)")
            }
        }

        return hsiPayload
    }

    companion object {
        private const val TAG = "SessionTracker"
    }

    /**
     * Compute behavioral metrics using Flux (Rust) - required.
     * Returns Pair of (metrics map, performance info).
     */
    /** Default metrics when Flux is not available (e.g. unit tests). Allows tests to run without native libs. */
    private fun defaultFluxMetrics(): Map<String, Any> {
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

    private fun computeBehavioralMetricsWithFlux(
            events: List<BehaviorEvent>,
            durationMs: Long,
            sessionStartTime: Long,
            sessionEndTime: Long
    ): Pair<Map<String, Any>?, Map<String, Any>> {
        // When Flux is not available (e.g. unit tests), return default metrics so callers don't throw
        if (!FluxBridge.isAvailable()) {
            android.util.Log.w(TAG, "Flux not available; using default metrics (e.g. in unit tests)")
            return Pair(defaultFluxMetrics(), emptyMap())
        }

        var fluxMetrics: Map<String, Any>? = null
        var fluxTimeMs: Long = 0

        try {
            val fluxStartTime = System.nanoTime()

            // Get device ID and timezone
            val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
            ) ?: "android-device"
            val timezone = TimeZone.getDefault().id

            // Convert events to synheart-flux JSON format
            val fluxJson = convertEventsToFluxJson(
                    sessionId = sessionId,
                    deviceId = deviceId,
                    timezone = timezone,
                    startTimeMs = sessionStartTime,
                    endTimeMs = sessionEndTime,
                    events = events
            )

            // Compute HSI using Flux
            val hsiJson = FluxBridge.behaviorToHsi(fluxJson)
            if (hsiJson != null) {
                // Extract metrics from HSI JSON
                val metrics = extractBehavioralMetricsFromHsi(hsiJson)
                if (metrics != null) {
                    fluxTimeMs = (System.nanoTime() - fluxStartTime) / 1_000_000
                    fluxMetrics = metrics
                    android.util.Log.d(
                            TAG,
                            "Successfully computed metrics using synheart-flux - ${fluxTimeMs}ms"
                    )
                } else {
                    fluxTimeMs = (System.nanoTime() - fluxStartTime) / 1_000_000
                    android.util.Log.w(TAG, "Failed to extract metrics from HSI JSON")
                }
            } else {
                fluxTimeMs = (System.nanoTime() - fluxStartTime) / 1_000_000
                android.util.Log.w(TAG, "Rust computation returned null (took ${fluxTimeMs}ms)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Flux computation failed: ${e.message}", e)
            fluxMetrics = defaultFluxMetrics()
        }

        // Build performance info; use default if Flux returned null
        val performanceInfo = mutableMapOf<String, Any>()
        val resultMetrics = fluxMetrics ?: defaultFluxMetrics()
        if (fluxMetrics != null) {
            performanceInfo["flux_execution_time_ms"] = fluxTimeMs
        }
        return Pair(resultMetrics, performanceInfo)
    }

    /**
     * Parse deep focus blocks from Flux metrics.
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
     * Convert Flux typing summary map to TypingSessionSummary object.
     */
    private fun convertFluxTypingSummaryToObject(fluxTypingSummary: Map<String, Any>): TypingSessionSummary {
        // Extract typing metrics list
        val typingMetricsList = (fluxTypingSummary["typing_metrics"] as? List<*>)?.mapNotNull { metric ->
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
                typingSessionCount = ((fluxTypingSummary["typing_session_count"] as? Number)?.toInt()) ?: 0,
                averageKeystrokesPerSession = ((fluxTypingSummary["average_keystrokes_per_session"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingSessionDuration = ((fluxTypingSummary["average_typing_session_duration"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingSpeed = ((fluxTypingSummary["average_typing_speed"] as? Number)?.toDouble()) ?: 0.0,
                averageTypingGap = ((fluxTypingSummary["average_typing_gap"] as? Number)?.toDouble()) ?: 0.0,
                averageInterTapInterval = ((fluxTypingSummary["average_inter_tap_interval"] as? Number)?.toDouble()) ?: 0.0,
                typingCadenceStability = ((fluxTypingSummary["typing_cadence_stability"] as? Number)?.toDouble()) ?: 0.0,
                burstinessOfTyping = ((fluxTypingSummary["burstiness_of_typing"] as? Number)?.toDouble()) ?: 0.0,
                totalTypingDuration = ((fluxTypingSummary["total_typing_duration"] as? Number)?.toInt()) ?: 0,
                activeTypingRatio = ((fluxTypingSummary["active_typing_ratio"] as? Number)?.toDouble()) ?: 0.0,
                typingContributionToInteractionIntensity = ((fluxTypingSummary["typing_contribution_to_interaction_intensity"] as? Number)?.toDouble()) ?: 0.0,
                deepTypingBlocks = ((fluxTypingSummary["deep_typing_blocks"] as? Number)?.toInt()) ?: 0,
                typingFragmentation = ((fluxTypingSummary["typing_fragmentation"] as? Number)?.toDouble()) ?: 0.0,
                clipboardActivityRate = ((fluxTypingSummary["clipboard_activity_rate"] as? Number)?.toDouble()) ?: 0.0,
                correctionRate = ((fluxTypingSummary["correction_rate"] as? Number)?.toDouble()) ?: 0.0,
                individualTypingSessions = typingMetricsList
        )
    }

    // Behavioral metrics are computed by synheart-flux (Rust); see computeBehavioralMetricsWithFlux().

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

        // Compute behavioral metrics using Flux (Rust) - required
        val (fluxMetrics, _) = computeBehavioralMetricsWithFlux(
                filteredEvents,
                duration,
                startTimestampMs,
                endTimestampMs
        )
        
        // Use flux metrics (or default when Flux was unavailable)
        val rangeMetrics = fluxMetrics ?: defaultFluxMetrics()

        // Extract behavioral metrics from Flux results
        val behavioralMetricsMap = rangeMetrics.filterKeys { key ->
            key != "typing_session_summary" // Separate typing summary
        }
        
        // Extract typing session summary from Flux results
        val fluxTypingSummary = rangeMetrics["typing_session_summary"] as? Map<String, Any>
        val typingSessionSummary = if (fluxTypingSummary != null && fluxTypingSummary.isNotEmpty()) {
            fluxTypingSummary
        } else {
            // Fallback to empty summary
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

        // Build and return metrics map using Flux results
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

    // Typing session summary is computed by synheart-flux; see extractBehavioralMetricsFromHsi / typing_session_summary.
}
