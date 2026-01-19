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
    private var libraryLoaded = false
    private var jniAvailable = false

    init {
        try {
            System.loadLibrary("synheart_flux")
            libraryLoaded = true
            Log.d(TAG, "Successfully loaded libsynheart_flux.so")

            // Test if JNI methods are actually available
            jniAvailable = testJniAvailability()
            if (jniAvailable) {
                Log.d(TAG, "JNI methods available - synheart-flux ready")
            } else {
                Log.w(TAG, "Library loaded but JNI methods not available")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load libsynheart_flux.so: ${e.message}")
            Log.w(TAG, "Falling back to Kotlin metric computation")
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

    /**
     * Check if the Rust library is available and JNI is properly configured.
     */
    val isAvailable: Boolean
        get() = libraryLoaded && jniAvailable

    /**
     * Convert behavioral session to HSI JSON (stateless, one-shot).
     *
     * @param sessionJson JSON string containing the behavioral session data
     * @return HSI JSON string, or null if computation failed
     */
    fun behaviorToHsi(sessionJson: String): String? {
        if (!isAvailable) {
            Log.w(TAG, "Rust library not available, cannot compute HSI")
            return null
        }
        return try {
            nativeBehaviorToHsi(sessionJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling behaviorToHsi: ${e.message}")
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
        if (!isAvailable) return 0L
        return try {
            nativeProcessorNew(baselineWindowSessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating processor: ${e.message}")
            0L
        }
    }

    /**
     * Free a processor created with createProcessor.
     */
    fun freeProcessor(handle: Long) {
        if (!isAvailable || handle == 0L) return
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
        if (!isAvailable || handle == 0L) return null
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
        if (!isAvailable || handle == 0L) return null
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
        if (!isAvailable || handle == 0L) return false
        return try {
            nativeProcessorLoadBaselines(handle, baselinesJson) == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error loading baselines: ${e.message}")
            false
        }
    }

    // Native method declarations - provided by libsynheart_flux.so
    private external fun nativeBehaviorToHsi(sessionJson: String): String?
    private external fun nativeProcessorNew(baselineWindowSessions: Int): Long
    private external fun nativeProcessorFree(handle: Long)
    private external fun nativeProcessorProcess(handle: Long, sessionJson: String): String?
    private external fun nativeProcessorSaveBaselines(handle: Long): String?
    private external fun nativeProcessorLoadBaselines(handle: Long, baselinesJson: String): Int
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
        if (!FluxBridge.isAvailable) {
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

    for (event in events) {
        val fluxEvent = JSONObject()
        fluxEvent.put("timestamp", event.timestamp)
        fluxEvent.put("event_type", event.eventType.name.lowercase())

        when (event.eventType) {
            BehaviorEventType.SCROLL -> {
                val scroll = JSONObject()
                scroll.put("velocity", event.metrics["velocity"] ?: 0.0)
                scroll.put("direction", event.metrics["direction"] ?: "down")
                scroll.put("direction_reversal", event.metrics["direction_reversal"] ?: false)
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
                interruption.put("action", event.metrics["action"] ?: "ignored")
                fluxEvent.put("interruption", interruption)
            }
            BehaviorEventType.TYPING -> {
                val typing = JSONObject()
                typing.put("typing_speed_cpm", event.metrics["typing_speed"] ?: 0.0)
                typing.put("cadence_stability", event.metrics["typing_cadence_stability"] ?: 0.0)
                fluxEvent.put("typing", typing)
            }
        }

        fluxEvents.put(fluxEvent)
    }

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
