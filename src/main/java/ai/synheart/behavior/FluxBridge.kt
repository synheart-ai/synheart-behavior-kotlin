package ai.synheart.behavior

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Bridge to synheart-flux Rust library for HSI-compliant behavioral metrics computation.
 *
 * This class provides JNI bindings to the Rust implementation of behavioral metrics,
 * ensuring consistent HSI-compliant output across all platforms.
 *
 * When synheart-flux is available, it computes:
 * - Distraction score and focus hint
 * - Burstiness (Barabasi formula)
 * - Task switch rate (exponential saturation)
 * - Notification load (exponential saturation)
 * - Scroll jitter rate
 * - Deep focus blocks
 * - Interaction intensity
 * - Rolling baselines across sessions
 */
object FluxBridge {
    private const val TAG = "FluxBridge"
    @Volatile private var libraryLoaded = false
    @Volatile private var jniAvailable = false
    @Volatile private var initializationAttempted = false
    @Volatile private var initializationError: Throwable? = null

    /**
     * Initialize Flux libraries. This is called lazily on first use.
     * Throws IllegalStateException if libraries cannot be loaded.
     */
    private fun ensureInitialized() {
        if (initializationAttempted) {
            initializationError?.let { throw IllegalStateException("Failed to load synheart-flux native libraries. Ensure libsynheart_flux.so and libflux_jni_bridge.so are available.", it) }
            return
        }

        synchronized(this) {
            if (initializationAttempted) {
                initializationError?.let { throw IllegalStateException("Failed to load synheart-flux native libraries. Ensure libsynheart_flux.so and libflux_jni_bridge.so are available.", it) }
                return
            }

            try {
                // First load the Rust library (libsynheart_flux.so)
                System.loadLibrary("synheart_flux")
                Log.d(TAG, "Successfully loaded libsynheart_flux.so")

                // Then load the JNI bridge library (libflux_jni_bridge.so)
                // This library links against libsynheart_flux.so and provides JNI functions
                System.loadLibrary("flux_jni_bridge")
                libraryLoaded = true
                Log.d(TAG, "Successfully loaded libflux_jni_bridge.so")

                // Test if JNI methods are actually available
                jniAvailable = testJniAvailability()
                if (jniAvailable) {
                    Log.d(TAG, "JNI methods available")
                } else {
                    Log.w(TAG, "Library loaded but JNI methods not available")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries: ${e.message}")
                Log.e(TAG, "synheart-flux is REQUIRED - SDK will fail if Flux is unavailable")
                Log.e(TAG, "Please download libsynheart_flux.so from https://github.com/synheart-ai/synheart-flux/releases")
                Log.e(TAG, "and place it in src/main/jniLibs/<abi>/ directory (e.g., src/main/jniLibs/arm64-v8a/)")
                initializationError = e
                throw IllegalStateException("Failed to load synheart-flux native libraries. Ensure libsynheart_flux.so and libflux_jni_bridge.so are available. Place them in src/main/jniLibs/<abi>/ directory.", e)
            } finally {
                initializationAttempted = true
            }
        }
    }

    private fun testJniAvailability(): Boolean {
        return try {
            // Try calling a native method to see if JNI is properly set up
            // This will throw UnsatisfiedLinkError if JNI methods aren't registered
            nativeProcessorNew(1).let { handle ->
                if (handle != 0L) {
                    nativeProcessorFree(handle)
                    true
                } else {
                    false
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "JNI methods not available: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error testing JNI availability: ${e.message}")
            false
        }
    }

    /** Check if the Rust library is available and JNI is properly configured. */
    fun isAvailable(): Boolean {
        return try {
            ensureInitialized()
            libraryLoaded && jniAvailable
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert behavioral session to HSI JSON (stateless, one-shot).
     *
     * @param sessionJson JSON string containing the behavioral session data
     * @return HSI JSON string, or null if computation failed
     */
    fun behaviorToHsi(sessionJson: String): String? {
        try {
            ensureInitialized()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot compute HSI: ${e.message}")
            throw e
        }
        
        if (!libraryLoaded || !jniAvailable) {
            Log.w(TAG, "Rust library not initialized, cannot compute HSI")
            return null
        }
        return try {
            Log.d(TAG, "Calling nativeBehaviorToHsi with JSON length: ${sessionJson.length}")
            val result = nativeBehaviorToHsi(sessionJson)
            if (result == null) {
                // Get error message from Rust
                try {
                    val errorMsg = nativeLastError()
                    Log.w(
                            TAG,
                            "Rust computation returned null. Error: ${errorMsg ?: "Unknown error (error message also null)"}"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get error message from Rust: ${e.message}")
                }
                Log.d(TAG, "Input JSON (first 500 chars): ${sessionJson.take(500)}")
            } else {
                Log.d(TAG, "Rust computation succeeded, result length: ${result.length}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling behaviorToHsi: ${e.message}", e)
            null
        }
    }

    /**
     * Create a stateful behavioral processor with the specified baseline window.
     *
     * @param baselineWindowSessions Number of sessions in the rolling baseline (default: 20)
     * @return Processor handle, or 0 if creation failed
     */
    fun createProcessor(baselineWindowSessions: Int = 20): Long {
        try {
            ensureInitialized()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot create processor: ${e.message}")
            throw e
        }
        if (!libraryLoaded || !jniAvailable) return 0
        return try {
            nativeProcessorNew(baselineWindowSessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating processor: ${e.message}")
            0
        }
    }

    /**
     * Free a processor created with createProcessor.
     */
    fun freeProcessor(handle: Long) {
        if (!isAvailable() || handle == 0L) return
        try {
            nativeProcessorFree(handle)
        } catch (e: Exception) {
            Log.e(TAG, "Error freeing processor: ${e.message}")
        }
    }

    /**
     * Process a behavioral session with the stateful processor.
     *
     * @param handle Processor handle from createProcessor
     * @param sessionJson JSON string containing the behavioral session data
     * @return HSI JSON string, or null if computation failed
     */
    fun processSession(handle: Long, sessionJson: String): String? {
        if (!isAvailable() || handle == 0L) return null
        return try {
            nativeProcessorProcess(handle, sessionJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing session: ${e.message}")
            null
        }
    }

    /**
     * Save baselines from a processor to JSON for persistence.
     */
    fun saveBaselines(handle: Long): String? {
        if (!isAvailable() || handle == 0L) return null
        return try {
            nativeProcessorSaveBaselines(handle)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving baselines: ${e.message}")
            null
        }
    }

    /**
     * Load baselines into a processor from JSON.
     *
     * @return true if loading succeeded, false otherwise
     */
    fun loadBaselines(handle: Long, baselinesJson: String): Boolean {
        if (!isAvailable() || handle == 0L) return false
        return try {
            nativeProcessorLoadBaselines(handle, baselinesJson) == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error loading baselines: ${e.message}")
            false
        }
    }

    // Native method declarations - provided by libflux_jni_bridge.so
    private external fun nativeBehaviorToHsi(sessionJson: String): String?
    private external fun nativeProcessorNew(baselineWindowSessions: Int): Long
    private external fun nativeProcessorFree(handle: Long)
    private external fun nativeProcessorProcess(handle: Long, sessionJson: String): String?
    private external fun nativeProcessorSaveBaselines(handle: Long): String?
    private external fun nativeProcessorLoadBaselines(handle: Long, baselinesJson: String): Int
    private external fun nativeLastError(): String?
}

/**
 * A stateful behavioral processor with persistent baselines.
 *
 * Use this class when you want baselines to accumulate across multiple sessions.
 */
class FluxBehaviorProcessor(baselineWindowSessions: Int = 20) : AutoCloseable {
    private val handle: Long
    private var disposed = false

    init {
        if (!FluxBridge.isAvailable()) {
            throw FluxError.LibraryNotAvailable
        }
        handle = FluxBridge.createProcessor(baselineWindowSessions)
        if (handle == 0L) {
            throw FluxError.ProcessorCreationFailed
        }
    }

    /**
     * Process a behavioral session and return HSI JSON.
     *
     * @param sessionJson JSON string containing the behavioral session data
     * @return HSI JSON string
     * @throws FluxError if processing fails
     */
    fun process(sessionJson: String): String {
        if (disposed) {
            throw FluxError.ProcessorDisposed
        }
        return FluxBridge.processSession(handle, sessionJson)
            ?: throw FluxError.ProcessingFailed
    }

    /**
     * Save current baselines to JSON for persistence.
     *
     * @return JSON string containing baseline data
     * @throws FluxError if saving fails
     */
    fun saveBaselines(): String {
        if (disposed) {
            throw FluxError.ProcessorDisposed
        }
        return FluxBridge.saveBaselines(handle)
            ?: throw FluxError.BaselineSaveFailed
    }

    /**
     * Load baselines from JSON.
     *
     * @param baselinesJson JSON string containing baseline data
     * @throws FluxError if loading fails
     */
    fun loadBaselines(baselinesJson: String) {
        if (disposed) {
            throw FluxError.ProcessorDisposed
        }
        if (!FluxBridge.loadBaselines(handle, baselinesJson)) {
            throw FluxError.BaselineLoadFailed
        }
    }

    /**
     * Dispose the processor and free native resources.
     */
    fun dispose() {
        if (!disposed) {
            disposed = true
            FluxBridge.freeProcessor(handle)
        }
    }

    override fun close() {
        dispose()
    }
}

/**
 * Errors that can occur when using FluxBridge.
 */
sealed class FluxError(message: String) : Exception(message) {
    object LibraryNotAvailable : FluxError("synheart-flux library is not available")
    object ProcessorCreationFailed : FluxError("Failed to create behavioral processor")
    object ProcessorDisposed : FluxError("Processor has been disposed")
    object ProcessingFailed : FluxError("Failed to process behavioral session")
    object BaselineSaveFailed : FluxError("Failed to save baselines")
    object BaselineLoadFailed : FluxError("Failed to load baselines")
    object InvalidJson : FluxError("Invalid JSON format")
}

/**
 * Convert session events to synheart-flux JSON format.
 */
fun convertEventsToFluxJson(
    sessionId: String,
    deviceId: String,
    timezone: String,
    startTimeMs: Long,
    endTimeMs: Long,
    events: List<BehaviorEvent>
): String {
    val fluxEvents = JSONArray()
    val isoFormatter = DateTimeFormatter.ISO_INSTANT

    // DEBUG: Count events being sent to Flux
    var scrollEventCount = 0
    var scrollEventsWithReversal = 0

    for (event in events) {
        // Count scroll events (APP_SWITCH events are not counted, but still sent to Flux)
        if (event.eventType == BehaviorEventType.SCROLL) {
            scrollEventCount++
            val hasReversal = event.metrics["direction_reversal"] as? Boolean ?: false
            if (hasReversal) {
                scrollEventsWithReversal++
            }
        }

        val fluxEvent = JSONObject()
        fluxEvent.put("timestamp", event.timestamp)
        fluxEvent.put("event_type", event.eventType.name.lowercase())

        when (event.eventType) {
            BehaviorEventType.SCROLL -> {
                val scroll = JSONObject()
                scroll.put("velocity", event.metrics["velocity"] ?: 0.0)
                scroll.put("direction", event.metrics["direction"] ?: "down")
                // Include direction_reversal if available (Flux accepts this field)
                // Only include if the metric exists, don't default to false
                val directionReversal = event.metrics["direction_reversal"]
                if (directionReversal != null) {
                    scroll.put("direction_reversal", directionReversal as? Boolean ?: false)
                }
                fluxEvent.put("scroll", scroll)
            }
            BehaviorEventType.TAP -> {
                val tap = JSONObject()
                tap.put("tap_duration_ms", event.metrics["tap_duration_ms"] ?: 0)
                tap.put("long_press", event.metrics["long_press"] ?: false)
                fluxEvent.put("tap", tap)
            }
            BehaviorEventType.SWIPE -> {
                val swipe = JSONObject()
                swipe.put("velocity", event.metrics["velocity"] ?: 0.0)
                swipe.put("direction", event.metrics["direction"] ?: "unknown")
                fluxEvent.put("swipe", swipe)
            }
            BehaviorEventType.NOTIFICATION, BehaviorEventType.CALL -> {
                val interruption = JSONObject()
                // Map action values to valid Rust enum values
                // Rust only accepts: ignored, opened, answered, dismissed
                val action = when (event.metrics["action"]?.toString()?.lowercase()) {
                    "opened", "open" -> "opened"
                    "answered", "answer" -> "answered"
                    "dismissed", "dismiss" -> "dismissed"
                    "received" -> "ignored" // Map "received" to "ignored" since it hasn't been acted upon
                    "ignored", "ignore" -> "ignored"
                    else -> "ignored" // Default to "ignored" for unknown values
                }
                interruption.put("action", action)
                // Include source_app_id if available (Flux accepts this field)
                val sourceAppId = event.metrics["source_app_id"]
                if (sourceAppId != null) {
                    interruption.put("source_app_id", sourceAppId.toString())
                }
                fluxEvent.put("interruption", interruption)
            }
            BehaviorEventType.TYPING -> {
                val typing = JSONObject()
                typing.put("typing_speed_cpm", event.metrics["typing_speed"] ?: 0.0)
                typing.put("cadence_stability", event.metrics["typing_cadence_stability"] ?: 0.0)
                // Include duration_sec if available (Flux accepts this field)
                val duration = event.metrics["duration"]
                if (duration != null) {
                    val durationSec = when (duration) {
                        is Number -> duration.toDouble()
                        is String -> duration.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    typing.put("duration_sec", durationSec)
                }
                // Include pause_count if available (Flux accepts this field)
                // Map typing_gap_count to pause_count as they represent the same concept
                val pauseCount = event.metrics["pause_count"] ?: event.metrics["typing_gap_count"]
                if (pauseCount != null) {
                    val pauseCountValue = when (pauseCount) {
                        is Number -> pauseCount.toInt()
                        is String -> pauseCount.toIntOrNull() ?: 0
                        else -> 0
                    }
                    typing.put("pause_count", pauseCountValue)
                }
                // Include detailed typing metrics that Flux uses for aggregation
                // These are needed for Flux to calculate average_keystrokes_per_session,
                // average_typing_gap, average_inter_tap_interval, and burstiness_of_typing
                val typingTapCount = event.metrics["typing_tap_count"]
                if (typingTapCount != null) {
                    val tapCountValue = when (typingTapCount) {
                        is Number -> typingTapCount.toInt()
                        is String -> typingTapCount.toIntOrNull() ?: 0
                        else -> 0
                    }
                    typing.put("typing_tap_count", tapCountValue)
                }
                val meanInterTapInterval = event.metrics["mean_inter_tap_interval_ms"]
                if (meanInterTapInterval != null) {
                    val itiValue = when (meanInterTapInterval) {
                        is Number -> meanInterTapInterval.toDouble()
                        is String -> meanInterTapInterval.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    typing.put("mean_inter_tap_interval_ms", itiValue)
                }
                val typingBurstiness = event.metrics["typing_burstiness"]
                if (typingBurstiness != null) {
                    val burstValue = when (typingBurstiness) {
                        is Number -> typingBurstiness.toDouble()
                        is String -> typingBurstiness.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    typing.put("typing_burstiness", burstValue)
                }
                // Include session boundaries if available
                val startAt = event.metrics["start_at"]
                if (startAt != null) {
                    typing.put("start_at", startAt.toString())
                }
                val endAt = event.metrics["end_at"]
                if (endAt != null) {
                    typing.put("end_at", endAt.toString())
                }
                fluxEvent.put("typing", typing)
            }
            BehaviorEventType.APP_SWITCH -> {
                // APP_SWITCH events are sent to Flux for task switch calculations
                // but not counted as one of the 6 event types (matching Dart SDK)
                val appSwitch = JSONObject()
                appSwitch.put("from_app_id", event.metrics["from_app_id"] ?: "")
                appSwitch.put("to_app_id", event.metrics["to_app_id"] ?: "")
                fluxEvent.put("app_switch", appSwitch)
            }
        }

        fluxEvents.put(fluxEvent)
    }

    // DEBUG: Log what we're sending to Flux
    Log.d("FluxBridge", "=== CONVERTING TO FLUX JSON ===")
    Log.d("FluxBridge", "Total events: ${events.size}")
    Log.d("FluxBridge", "Scroll events: $scrollEventCount (with reversal: $scrollEventsWithReversal)")
    Log.d("FluxBridge", "=== END CONVERSION DEBUG ===")

    val session = JSONObject()
    session.put("session_id", sessionId)
    session.put("device_id", deviceId)
    session.put("timezone", timezone)
    session.put("start_time", isoFormatter.format(Instant.ofEpochMilli(startTimeMs)))
    session.put("end_time", isoFormatter.format(Instant.ofEpochMilli(endTimeMs)))
    session.put("events", fluxEvents)

    return session.toString()
}

/**
 * Parse HSI JSON response into HsiBehaviorPayload.
 *
 * @param hsiJson JSON string from synheart-flux
 * @return Parsed HSI payload, or null if parsing fails
 */
fun parseHsiJson(hsiJson: String): HsiBehaviorPayload? {
    return try {
        val json = JSONObject(hsiJson)

        // Parse producer
        val producerJson = json.optJSONObject("producer")
        val producer = HsiProducer(
            name = producerJson?.optString("name") ?: "synheart-flux",
            version = producerJson?.optString("version") ?: "0.1.0",
            instanceId = producerJson?.optString("instance_id") ?: ""
        )

        // Parse provenance
        val provenanceJson = json.optJSONObject("provenance")
        val provenance = HsiProvenance(
            sourceDeviceId = provenanceJson?.optString("source_device_id") ?: "",
            observedAtUtc = provenanceJson?.optString("observed_at_utc") ?: "",
            computedAtUtc = provenanceJson?.optString("computed_at_utc") ?: ""
        )

        // Parse quality
        val qualityJson = json.optJSONObject("quality")
        val flagsArray = qualityJson?.optJSONArray("flags")
        val flags = mutableListOf<String>()
        if (flagsArray != null) {
            for (i in 0 until flagsArray.length()) {
                flags.add(flagsArray.getString(i))
            }
        }
        val quality = HsiQuality(
            coverage = qualityJson?.optDouble("coverage") ?: 0.0,
            confidence = qualityJson?.optDouble("confidence") ?: 0.0,
            flags = flags
        )

        // Parse behavior windows
        val windowsArray = json.optJSONArray("behavior_windows")
        val windows = mutableListOf<HsiBehaviorWindow>()
        if (windowsArray != null) {
            for (i in 0 until windowsArray.length()) {
                val windowJson = windowsArray.getJSONObject(i)
                val behaviorJson = windowJson.optJSONObject("behavior")
                val baselineJson = windowJson.optJSONObject("baseline")
                val eventSummaryJson = windowJson.optJSONObject("event_summary")

                // Parse deep focus blocks
                val deepFocusBlocksArray = behaviorJson?.optJSONArray("deep_focus_blocks")
                val deepFocusBlockCount = if (deepFocusBlocksArray != null) {
                    deepFocusBlocksArray.length()
                } else {
                    behaviorJson?.optInt("deep_focus_blocks") ?: 0
                }

                val behavior = HsiBehavioralMetrics(
                    distractionScore = behaviorJson?.optDouble("distraction_score") ?: 0.0,
                    focusHint = behaviorJson?.optDouble("focus_hint") ?: 0.0,
                    taskSwitchRate = behaviorJson?.optDouble("task_switch_rate") ?: 0.0,
                    notificationLoad = behaviorJson?.optDouble("notification_load") ?: 0.0,
                    burstiness = behaviorJson?.optDouble("burstiness") ?: 0.0,
                    scrollJitterRate = behaviorJson?.optDouble("scroll_jitter_rate") ?: 0.0,
                    interactionIntensity = behaviorJson?.optDouble("interaction_intensity") ?: 0.0,
                    deepFocusBlocks = deepFocusBlockCount,
                    idleRatio = behaviorJson?.optDouble("idle_ratio") ?: 0.0,
                    fragmentedIdleRatio = behaviorJson?.optDouble("fragmented_idle_ratio") ?: 0.0
                )

                val baseline = if (baselineJson != null) {
                    HsiBehaviorBaseline(
                        distraction = if (baselineJson.has("distraction")) baselineJson.optDouble("distraction") else null,
                        focus = if (baselineJson.has("focus")) baselineJson.optDouble("focus") else null,
                        distractionDeviationPct = if (baselineJson.has("distraction_deviation_pct")) baselineJson.optDouble("distraction_deviation_pct") else null,
                        sessionsInBaseline = baselineJson.optInt("sessions_in_baseline")
                    )
                } else null

                val eventSummary = HsiEventSummary(
                    totalEvents = eventSummaryJson?.optInt("total_events") ?: 0,
                    scrollEvents = eventSummaryJson?.optInt("scroll_events") ?: 0,
                    tapEvents = eventSummaryJson?.optInt("tap_events") ?: 0,
                    appSwitches = eventSummaryJson?.optInt("app_switches") ?: 0,
                    notifications = eventSummaryJson?.optInt("notifications") ?: 0
                )

                windows.add(
                    HsiBehaviorWindow(
                        sessionId = windowJson.optString("session_id"),
                        startTimeUtc = windowJson.optString("start_time_utc"),
                        endTimeUtc = windowJson.optString("end_time_utc"),
                        durationSec = windowJson.optDouble("duration_sec"),
                        behavior = behavior,
                        baseline = baseline,
                        eventSummary = eventSummary
                    )
                )
            }
        }

        HsiBehaviorPayload(
            hsiVersion = json.optString("hsi_version", "1.0.0"),
            producer = producer,
            provenance = provenance,
            quality = quality,
            behaviorWindows = windows
        )
    } catch (e: Exception) {
        Log.e("FluxBridge", "Failed to parse HSI JSON: ${e.message}")
        null
    }
}

/**
 * Extract behavioral metrics from HSI JSON in the format expected by the SDK.
 */
fun extractBehavioralMetricsFromHsi(hsiJson: String): Map<String, Any>? {
    return try {
        val hsi = JSONObject(hsiJson)

        // HSI 1.0 format: axes.behavior.readings array
        val axes = hsi.optJSONObject("axes") ?: return null
        val behavior = axes.optJSONObject("behavior") ?: return null
        val readings = behavior.optJSONArray("readings") ?: return null

        // Extract metrics from axis readings
        val metricsMap = mutableMapOf<String, Double>()
        for (i in 0 until readings.length()) {
            val reading = readings.getJSONObject(i)
            val axis = reading.optString("axis", "")
            val score = reading.optDouble("score", Double.NaN)
            if (!score.isNaN()) {
                metricsMap[axis] = score
            }
        }

        // Extract meta information
        val meta = hsi.optJSONObject("meta")

        // Build result map with SDK-expected field names
        val result = mutableMapOf<String, Any>()
        result["interaction_intensity"] = metricsMap["interaction_intensity"] ?: 0.0
        
        // Task switch rate: Direct extraction from Flux (exponential saturation formula)
        val taskSwitchRate = metricsMap["task_switch_rate"] ?: 0.0
        result["task_switch_rate"] = taskSwitchRate
        Log.d("FluxBridge", "Extracted task_switch_rate from Flux: $taskSwitchRate")
        
        // Task switch cost: Flux outputs normalized 0-1, convert to raw ms (0-10000)
        val taskSwitchCostNormalized = metricsMap["task_switch_cost"] ?: 0.0
        val taskSwitchCostMs = (taskSwitchCostNormalized * 10000.0).toInt()
        result["task_switch_cost"] = taskSwitchCostMs
        Log.d("FluxBridge", "Extracted task_switch_cost from Flux: normalized=$taskSwitchCostNormalized, raw_ms=$taskSwitchCostMs")
        
        result["idle_time_ratio"] = metricsMap["idle_ratio"] ?: 0.0
        
        // Active time ratio: Flux outputs directly, fallback to 1.0 - idle_ratio
        val activeTimeRatio = metricsMap["active_time_ratio"] ?: (1.0 - (metricsMap["idle_ratio"] ?: 0.0))
        result["active_time_ratio"] = activeTimeRatio
        Log.d("FluxBridge", "Extracted active_time_ratio from Flux: $activeTimeRatio (idle_ratio=${metricsMap["idle_ratio"] ?: 0.0})")
        
        result["notification_load"] = metricsMap["notification_load"] ?: 0.0
        result["burstiness"] = metricsMap["burstiness"] ?: 0.0
        result["behavioral_distraction_score"] = metricsMap["distraction"] ?: 0.0
        result["focus_hint"] = metricsMap["focus"] ?: 0.0
        result["fragmented_idle_ratio"] = metricsMap["fragmented_idle_ratio"] ?: 0.0
        
        // Scroll jitter rate: Direct extraction from Flux (reversals / (scroll_events - 1))
        val scrollJitterRate = metricsMap["scroll_jitter_rate"] ?: 0.0
        result["scroll_jitter_rate"] = scrollJitterRate
        Log.d("FluxBridge", "Extracted scroll_jitter_rate from Flux: $scrollJitterRate")
        // Extract deep focus blocks detail as a List (matching Kotlin's format)
        val deepFocusBlocksDetail = meta?.optJSONArray("deep_focus_blocks_detail")
        result["deep_focus_blocks"] =
                if (deepFocusBlocksDetail != null) {
                    extractDeepFocusBlocks(deepFocusBlocksDetail)
                } else {
                    emptyList<Map<String, Any>>()
                }
        result["sessions_in_baseline"] = meta?.optInt("sessions_in_baseline") ?: 0

        // Add baseline info from meta if available
        meta?.optDouble("baseline_distraction")?.let { result["baseline_distraction"] = it }
        meta?.optDouble("baseline_focus")?.let { result["baseline_focus"] = it }
        meta?.optDouble("distraction_deviation_pct")?.let {
            result["distraction_deviation_pct"] = it
        }

        // Extract typing session summary from Flux's meta
        val typingSummary = extractTypingSessionSummary(meta)
        result["typing_session_summary"] = typingSummary

        result
    } catch (e: Exception) {
        Log.e("FluxBridge", "Failed to extract metrics from HSI: ${e.message}", e)
        null
    }
}

private fun extractDeepFocusBlocks(blocks: JSONArray?): List<Map<String, Any>> {
    if (blocks == null) return emptyList()
    return (0 until blocks.length()).map { i ->
        val block = blocks.getJSONObject(i)
        mapOf(
                "start_at" to block.optString("start_at", ""),
                "end_at" to block.optString("end_at", ""),
                "duration_ms" to block.optInt("duration_ms", 0)
        )
    }
}

/** Extract typing session summary from Flux's meta section. */
private fun extractTypingSessionSummary(meta: JSONObject?): Map<String, Any> {
    if (meta == null) {
        return emptyMap()
    }

    val typingSessionCount = meta.optInt("typing_session_count") ?: 0

    // Always return the map (even if all zeros) so we can compare with Calculation
    return mapOf(
            "typing_session_count" to typingSessionCount,
            "average_keystrokes_per_session" to
                    (meta.optDouble("average_keystrokes_per_session") ?: 0.0),
            "average_typing_session_duration" to
                    (meta.optDouble("average_typing_session_duration") ?: 0.0),
            "average_typing_speed" to (meta.optDouble("average_typing_speed") ?: 0.0),
            "average_typing_gap" to (meta.optDouble("average_typing_gap") ?: 0.0),
            "average_inter_tap_interval" to (meta.optDouble("average_inter_tap_interval") ?: 0.0),
            "typing_cadence_stability" to (meta.optDouble("typing_cadence_stability") ?: 0.0),
            "burstiness_of_typing" to (meta.optDouble("burstiness_of_typing") ?: 0.0),
            "total_typing_duration" to (meta.optInt("total_typing_duration") ?: 0),
            "active_typing_ratio" to (meta.optDouble("active_typing_ratio") ?: 0.0),
            "typing_contribution_to_interaction_intensity" to
                    (meta.optDouble("typing_contribution_to_interaction_intensity") ?: 0.0),
            "deep_typing_blocks" to (meta.optInt("deep_typing_blocks") ?: 0),
            "typing_fragmentation" to (meta.optDouble("typing_fragmentation") ?: 0.0),
            "typing_metrics" to extractTypingMetrics(meta.optJSONArray("typing_metrics"))
    )
}

/** Extract individual typing session metrics from Flux's typing_metrics array. */
private fun extractTypingMetrics(metricsArray: JSONArray?): List<Map<String, Any>> {
    if (metricsArray == null) return emptyList()
    return (0 until metricsArray.length()).map { i ->
        val metric = metricsArray.getJSONObject(i)
        mapOf(
                "start_at" to metric.optString("start_at", ""),
                "end_at" to metric.optString("end_at", ""),
                "duration" to metric.optInt("duration", 0),
                "deep_typing" to (metric.optBoolean("deep_typing") ?: false),
                "typing_tap_count" to metric.optInt("typing_tap_count", 0),
                "typing_speed" to metric.optDouble("typing_speed", 0.0),
                "mean_inter_tap_interval_ms" to metric.optDouble("mean_inter_tap_interval_ms", 0.0),
                "typing_cadence_variability" to metric.optDouble("typing_cadence_variability", 0.0),
                "typing_cadence_stability" to metric.optDouble("typing_cadence_stability", 0.0),
                "typing_gap_count" to metric.optInt("typing_gap_count", 0),
                "typing_gap_ratio" to metric.optDouble("typing_gap_ratio", 0.0),
                "typing_burstiness" to metric.optDouble("typing_burstiness", 0.0),
                "typing_activity_ratio" to metric.optDouble("typing_activity_ratio", 0.0),
                "typing_interaction_intensity" to
                        metric.optDouble("typing_interaction_intensity", 0.0)
        )
    }
}

/**
 * Extract behavioral metrics dictionary from HSI payload.
 *
 * This returns metrics in a format compatible with the existing SDK output.
 *
 * @param hsi Parsed HSI payload
 * @return Map of behavioral metrics
 */
fun extractMetricsDictionary(hsi: HsiBehaviorPayload): Map<String, Any>? {
    val window = hsi.behaviorWindows.firstOrNull() ?: return null

    val metrics = mutableMapOf<String, Any>(
        "distraction_score" to window.behavior.distractionScore,
        "focus_hint" to window.behavior.focusHint,
        "task_switch_rate" to window.behavior.taskSwitchRate,
        "notification_load" to window.behavior.notificationLoad,
        "burstiness" to window.behavior.burstiness,
        "scroll_jitter_rate" to window.behavior.scrollJitterRate,
        "interaction_intensity" to window.behavior.interactionIntensity,
        "deep_focus_blocks" to window.behavior.deepFocusBlocks,
        "idle_ratio" to window.behavior.idleRatio,
        "fragmented_idle_ratio" to window.behavior.fragmentedIdleRatio,
        "total_events" to window.eventSummary.totalEvents,
        "scroll_events" to window.eventSummary.scrollEvents,
        "tap_events" to window.eventSummary.tapEvents,
        "app_switches" to window.eventSummary.appSwitches,
        "notifications" to window.eventSummary.notifications
    )

    window.baseline?.let { baseline ->
        baseline.distraction?.let { metrics["baseline_distraction"] = it }
        baseline.focus?.let { metrics["baseline_focus"] = it }
        baseline.distractionDeviationPct?.let { metrics["distraction_deviation_pct"] = it }
        metrics["sessions_in_baseline"] = baseline.sessionsInBaseline
    }

    return metrics
}
