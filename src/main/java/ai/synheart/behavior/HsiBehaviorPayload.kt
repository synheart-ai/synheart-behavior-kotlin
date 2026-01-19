package ai.synheart.behavior

import org.json.JSONArray
import org.json.JSONObject

/**
 * HSI-compliant behavioral metrics computed by synheart-flux.
 */
data class HsiBehavioralMetrics(
    /** Composite distraction score (0.0 to 1.0) */
    val distractionScore: Double,
    /** Focus hint (inverse of distraction, 0.0 to 1.0) */
    val focusHint: Double,
    /** Task switch rate (0.0 to 1.0, exponential saturation) */
    val taskSwitchRate: Double,
    /** Notification load (0.0 to 1.0, exponential saturation) */
    val notificationLoad: Double,
    /** Burstiness index (Barabasi formula, 0.0 to 1.0) */
    val burstiness: Double,
    /** Scroll jitter rate (direction reversals / scroll events) */
    val scrollJitterRate: Double,
    /** Interaction intensity (events per second) */
    val interactionIntensity: Double,
    /** Number of deep focus blocks (engagement >= 120s) */
    val deepFocusBlocks: Int,
    /** Idle ratio (time in idle / session duration) */
    val idleRatio: Double,
    /** Fragmented idle ratio (idle segments / duration) */
    val fragmentedIdleRatio: Double
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("distraction_score", distractionScore)
            put("focus_hint", focusHint)
            put("task_switch_rate", taskSwitchRate)
            put("notification_load", notificationLoad)
            put("burstiness", burstiness)
            put("scroll_jitter_rate", scrollJitterRate)
            put("interaction_intensity", interactionIntensity)
            put("deep_focus_blocks", deepFocusBlocks)
            put("idle_ratio", idleRatio)
            put("fragmented_idle_ratio", fragmentedIdleRatio)
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "distraction_score" to distractionScore,
            "focus_hint" to focusHint,
            "task_switch_rate" to taskSwitchRate,
            "notification_load" to notificationLoad,
            "burstiness" to burstiness,
            "scroll_jitter_rate" to scrollJitterRate,
            "interaction_intensity" to interactionIntensity,
            "deep_focus_blocks" to deepFocusBlocks,
            "idle_ratio" to idleRatio,
            "fragmented_idle_ratio" to fragmentedIdleRatio
        )
    }
}

/**
 * Baseline data for behavioral metrics.
 */
data class HsiBehaviorBaseline(
    /** Baseline distraction score */
    val distraction: Double?,
    /** Baseline focus score */
    val focus: Double?,
    /** Deviation from baseline (percentage) */
    val distractionDeviationPct: Double?,
    /** Number of sessions in baseline */
    val sessionsInBaseline: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            distraction?.let { put("distraction", it) }
            focus?.let { put("focus", it) }
            distractionDeviationPct?.let { put("distraction_deviation_pct", it) }
            put("sessions_in_baseline", sessionsInBaseline)
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "distraction" to distraction,
            "focus" to focus,
            "distraction_deviation_pct" to distractionDeviationPct,
            "sessions_in_baseline" to sessionsInBaseline
        )
    }
}

/**
 * Event summary for a behavioral session.
 */
data class HsiEventSummary(
    val totalEvents: Int,
    val scrollEvents: Int,
    val tapEvents: Int,
    val appSwitches: Int,
    val notifications: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("total_events", totalEvents)
            put("scroll_events", scrollEvents)
            put("tap_events", tapEvents)
            put("app_switches", appSwitches)
            put("notifications", notifications)
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "total_events" to totalEvents,
            "scroll_events" to scrollEvents,
            "tap_events" to tapEvents,
            "app_switches" to appSwitches,
            "notifications" to notifications
        )
    }
}

/**
 * A behavioral window in HSI format.
 */
data class HsiBehaviorWindow(
    val sessionId: String,
    val startTimeUtc: String,
    val endTimeUtc: String,
    val durationSec: Double,
    val behavior: HsiBehavioralMetrics,
    val baseline: HsiBehaviorBaseline?,
    val eventSummary: HsiEventSummary
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("session_id", sessionId)
            put("start_time_utc", startTimeUtc)
            put("end_time_utc", endTimeUtc)
            put("duration_sec", durationSec)
            put("behavior", behavior.toJson())
            baseline?.let { put("baseline", it.toJson()) }
            put("event_summary", eventSummary.toJson())
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "session_id" to sessionId,
            "start_time_utc" to startTimeUtc,
            "end_time_utc" to endTimeUtc,
            "duration_sec" to durationSec,
            "behavior" to behavior.toMap(),
            "baseline" to baseline?.toMap(),
            "event_summary" to eventSummary.toMap()
        )
    }
}

/**
 * HSI producer metadata.
 */
data class HsiProducer(
    val name: String,
    val version: String,
    val instanceId: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("version", version)
            put("instance_id", instanceId)
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "version" to version,
            "instance_id" to instanceId
        )
    }
}

/**
 * HSI provenance metadata.
 */
data class HsiProvenance(
    val sourceDeviceId: String,
    val observedAtUtc: String,
    val computedAtUtc: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("source_device_id", sourceDeviceId)
            put("observed_at_utc", observedAtUtc)
            put("computed_at_utc", computedAtUtc)
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "source_device_id" to sourceDeviceId,
            "observed_at_utc" to observedAtUtc,
            "computed_at_utc" to computedAtUtc
        )
    }
}

/**
 * HSI quality metadata.
 */
data class HsiQuality(
    val coverage: Double,
    val confidence: Double,
    val flags: List<String>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("coverage", coverage)
            put("confidence", confidence)
            put("flags", JSONArray(flags))
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "coverage" to coverage,
            "confidence" to confidence,
            "flags" to flags
        )
    }
}

/**
 * Complete HSI behavioral payload.
 */
data class HsiBehaviorPayload(
    val hsiVersion: String,
    val producer: HsiProducer,
    val provenance: HsiProvenance,
    val quality: HsiQuality,
    val behaviorWindows: List<HsiBehaviorWindow>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("hsi_version", hsiVersion)
            put("producer", producer.toJson())
            put("provenance", provenance.toJson())
            put("quality", quality.toJson())
            put("behavior_windows", JSONArray(behaviorWindows.map { it.toJson() }))
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "hsi_version" to hsiVersion,
            "producer" to producer.toMap(),
            "provenance" to provenance.toMap(),
            "quality" to quality.toMap(),
            "behavior_windows" to behaviorWindows.map { it.toMap() }
        )
    }

    /**
     * Get the first behavior window's metrics, if available.
     */
    fun getFirstWindowMetrics(): HsiBehavioralMetrics? {
        return behaviorWindows.firstOrNull()?.behavior
    }

    /**
     * Get the first behavior window's baseline, if available.
     */
    fun getFirstWindowBaseline(): HsiBehaviorBaseline? {
        return behaviorWindows.firstOrNull()?.baseline
    }
}
