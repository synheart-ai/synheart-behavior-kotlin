package ai.synheart.behavior.internal

import ai.synheart.behavior.*
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

        // Raw counts only. Derived rates (ignore_rate, clustering_index),
        // window-level behavioral metrics (distraction_score, burstiness,
        // focus_hint, …), and the typing session summary are computed
        // downstream from the emitted event stream.
        val notificationEvents = events.filter { it.eventType == BehaviorEventType.NOTIFICATION }
        val notificationCount = notificationEvents.size
        val notificationIgnored =
                notificationEvents.count { (it.metrics["action"] as? String) == "ignored" }

        val callEvents = events.filter { it.eventType == BehaviorEventType.CALL }
        val callCount = callEvents.size
        val callIgnored = callEvents.count { (it.metrics["action"] as? String) == "ignored" }

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

        // Notification Summary — raw counts only.
        // notificationIgnoreRate / notificationClusteringIndex left at 0.0
        // for the runtime to overwrite.
        val notificationSummary =
                NotificationSummary(
                        notificationCount = notificationCount,
                        notificationIgnored = notificationIgnored,
                        notificationIgnoreRate = 0.0,
                        notificationClusteringIndex = 0.0,
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

        // Motion State will be computed by SynheartBehavior after inference.
        // typingSessionSummary is computed downstream from the emitted
        // typing events.
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
                behavioralMetrics = null,
                notificationSummary = notificationSummary,
                systemState = systemState,
                typingSessionSummary = null,
                motionData = motionData
        )
    }

    companion object {
        private const val TAG = "SessionTracker"
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
}
