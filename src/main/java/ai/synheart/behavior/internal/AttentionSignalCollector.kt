package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlinx.coroutines.*

/**
 * Collector for attention and multitasking signals: app switching, idle gaps, session stability.
 * Monitors user attention patterns and task fragmentation.
 */
@android.annotation.SuppressLint("NewApi")
internal class AttentionSignalCollector(
        private val sessionId: String,
        private val application: Application,
        private val maxIdleGapSeconds: Double,
        initialAppSwitchCount: Int = 0
) : SignalCollector {

    private var eventCallback: ((BehaviorEvent) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isCollecting = false

    // App lifecycle tracking
    private val appSwitchTimestamps = ConcurrentLinkedQueue<Long>()
    private var foregroundStartTime: Long? = null
    private var backgroundStartTime: Long = 0
    private var isInForeground = true
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var currentIdleGapStart: Long? = null

    // Idle gap tracking
    private val idleGaps = ConcurrentLinkedQueue<IdleGap>()
    private val microIdleThreshold = 2000L // 2 seconds
    private val midIdleThreshold = 5000L // 5 seconds

    // Session stability tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var totalForegroundTime: Long = 0
    private var totalBackgroundTime: Long = 0
    private var totalIdleTime: Long = 0
    private var taskSwitchCount = initialAppSwitchCount

    // Phone call tracking
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var lastCallState: Int = TelephonyManager.CALL_STATE_IDLE
    private var callStartTime: Long? = null

    // Notification tracking
    private var notificationCount = 0
    private var notificationIgnoredCount = 0
    private val notificationTimestamps = ConcurrentLinkedQueue<Long>()

    // Activity lifecycle callback
    private val lifecycleCallbacks =
            object : Application.ActivityLifecycleCallbacks {
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
        isInForeground = true
        backgroundStartTime = 0

        // Start phone call monitoring
        try {
            telephonyManager =
                    application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            // Check if permission is granted
            if (PackageManager.PERMISSION_GRANTED ==
                            ContextCompat.checkSelfPermission(
                                    application,
                                    Manifest.permission.READ_PHONE_STATE
                            )
            ) {
                phoneStateListener =
                        object : PhoneStateListener() {
                            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                                handleCallStateChange(state)
                            }
                        }
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            } else {
                android.util.Log.d(
                        "AttentionCollector",
                        "READ_PHONE_STATE permission not granted - call events will not be tracked"
                )
            }
        } catch (e: SecurityException) {
            android.util.Log.w(
                    "AttentionCollector",
                    "SecurityException setting up phone state listener: ${e.message}"
            )
        } catch (e: Exception) {
            android.util.Log.w(
                    "AttentionCollector",
                    "Exception setting up phone state listener: ${e.message}"
            )
        }

        // Start monitoring idle gaps
        scope.launch {
            while (isCollecting) {
                delay(1000) // Check every second
                checkForIdleGap()
            }
        }
    }

    override fun stop() {
        isCollecting = false
        application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        scope.cancel()

        // Stop phone call monitoring
        phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        phoneStateListener = null
        telephonyManager = null

        // Record final foreground duration
        foregroundStartTime?.let { totalForegroundTime += (System.currentTimeMillis() - it) }
    }

    /** Reinitialize phone state listener (call this after permission is granted). */
    fun reinitializePhoneStateListener() {
        if (!isCollecting) return

        // Stop existing listener if any
        phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }

        // Try to set up phone state listener again
        try {
            telephonyManager =
                    application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (PackageManager.PERMISSION_GRANTED ==
                            ContextCompat.checkSelfPermission(
                                    application,
                                    Manifest.permission.READ_PHONE_STATE
                            )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val telephonyManager =
                            application.getSystemService(Context.TELEPHONY_SERVICE) as
                                    TelephonyManager
                    telephonyManager.registerTelephonyCallback(
                            application.mainExecutor,
                            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                                override fun onCallStateChanged(state: Int) {
                                    handleCallStateChange(state)
                                }
                            }
                    )
                } else {
                    val telephonyManager =
                            application.getSystemService(Context.TELEPHONY_SERVICE) as
                                    TelephonyManager
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(
                            phoneStateListener,
                            android.telephony.PhoneStateListener.LISTEN_CALL_STATE
                    )
                }

                android.util.Log.d("AttentionCollector", "Phone state listener initialized")
            } else {
                android.util.Log.w("AttentionCollector", "READ_PHONE_STATE permission not granted")
            }
        } catch (e: SecurityException) {
            android.util.Log.w(
                    "AttentionCollector",
                    "SecurityException reinitializing phone state listener: ${e.message}"
            )
        } catch (e: Exception) {
            android.util.Log.w(
                    "AttentionCollector",
                    "Exception reinitializing phone state listener: ${e.message}"
            )
        }
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventCallback = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        val currentTime = System.currentTimeMillis()
        val recentSwitches = appSwitchTimestamps.count { it > currentTime - 60000 }
        val currentForegroundDuration = foregroundStartTime?.let { (currentTime - it) / 1000.0 }
        val currentIdleGap = currentIdleGapStart?.let { (currentTime - it) / 1000.0 }

        return mapOf(
                "appSwitchesPerMinute" to recentSwitches,
                "foregroundDuration" to currentForegroundDuration,
                "idleGapSeconds" to currentIdleGap,
                "stabilityIndex" to calculateStabilityIndex(),
                "fragmentationIndex" to calculateFragmentationIndex()
        )
    }

    fun getAppSwitchCount(): Int = taskSwitchCount

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
        notificationTimestamps.clear()
        foregroundStartTime = System.currentTimeMillis()
        lastActivityTime = System.currentTimeMillis()
        currentIdleGapStart = null
        sessionStartTime = System.currentTimeMillis()
        totalForegroundTime = 0
        totalIdleTime = 0
        taskSwitchCount = 0
        notificationCount = 0
        notificationIgnoredCount = 0
        lastCallState = TelephonyManager.CALL_STATE_IDLE
        callStartTime = null
    }

    private fun cleanOldNotificationTimestamps(currentTime: Long, maxAgeMs: Long) {
        while (notificationTimestamps.peek()?.let { currentTime - it > maxAgeMs } == true) {
            notificationTimestamps.poll()
        }
    }

    /** Called when user performs any activity (typing, scrolling, tapping). */
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
        foregroundStartTime = currentTime

        if (!isInForeground) {
            isInForeground = true

            // Calculate background duration
            val backgroundDuration =
                    if (backgroundStartTime > 0) {
                        currentTime - backgroundStartTime
                    } else 0

            totalBackgroundTime += backgroundDuration

            // Emit app switch event if we had a background period
            // Note: App switch count is incremented when going to background, not when returning
            if (backgroundDuration > 0) {
                emitAppSwitchEvent(backgroundDuration)
            }
        }

        // Clean old timestamps (keep last 60 seconds)
        cleanOldTimestamps(currentTime, 60000)
    }

    private fun onAppBackgrounded() {
        val currentTime = System.currentTimeMillis()
        backgroundStartTime = currentTime

        if (isInForeground) {
            isInForeground = false

            // Calculate foreground duration
            foregroundStartTime?.let {
                val duration = (currentTime - it) / 1000.0
                totalForegroundTime += (currentTime - it)
                emitForegroundDurationEvent(duration)
            }

            // Count app switch when going to background (this is when the switch actually happens)
            // This ensures app switch is counted even if session is auto-ended while in background
            taskSwitchCount++
            appSwitchTimestamps.offer(currentTime)
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

    private fun emitAppSwitchEvent(backgroundDuration: Long) {
        // Emit app switch event with from_app_id and to_app_id (empty strings if not available)
        emitEvent(
                BehaviorEventType.APP_SWITCH,
                mapOf(
                        "from_app_id" to "",
                        "to_app_id" to "",
                        "background_duration_ms" to backgroundDuration.toInt()
                )
        )
    }

    // These metrics are tracked internally for stats calculation but NOT emitted as events
    // (matching Flutter SDK behavior - see EXAMPLE_APP_GUIDE.md)
    private fun emitForegroundDurationEvent(duration: Double) {
        // Foreground duration is tracked internally for getCurrentStats(), not emitted as event
        // This matches Flutter SDK: "App lifecycle events are tracked internally by the SDK"
    }

    private fun emitIdleGapEvent(duration: Double) {
        // Idle gaps are tracked internally for getCurrentStats(), not emitted as event
        // This matches Flutter SDK: "Idle gaps are tracked internally and available via
        // getCurrentStats(), not as individual events"
    }

    private fun emitMicroIdleEvent(duration: Double) {
        // Micro idle is tracked internally for getCurrentStats(), not emitted as event
    }

    private fun emitMidIdleEvent(duration: Double) {
        // Mid idle is tracked internally for getCurrentStats(), not emitted as event
    }

    private fun emitTaskDropIdleEvent(duration: Double) {
        // Task drop idle is tracked internally for getCurrentStats(), not emitted as event
    }

    private fun emitSessionStabilityEvent() {
        // Stability index is computed for getCurrentStats(), not emitted as event
        // This matches Flutter SDK behavior
    }

    private fun emitFragmentationIndexEvent() {
        // Fragmentation index is computed for getCurrentStats(), not emitted as event
        // This matches Flutter SDK behavior
    }

    private fun handleCallStateChange(state: Int) {
        val currentTime = System.currentTimeMillis()

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call
                if (lastCallState == TelephonyManager.CALL_STATE_IDLE) {
                    callStartTime = currentTime
                    emitCallEvent("ringing")
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered
                if (lastCallState == TelephonyManager.CALL_STATE_RINGING) {
                    emitCallEvent("answered")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended or ignored
                if (lastCallState == TelephonyManager.CALL_STATE_RINGING) {
                    // Call was ringing but now idle - likely ignored
                    emitCallEvent("ignored")
                } else if (lastCallState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // Call was active but now idle - call ended
                    emitCallEvent("ended")
                }
                callStartTime = null
            }
        }

        lastCallState = state
    }

    private fun emitCallEvent(action: String) {
        emitEvent(
                BehaviorEventType.CALL,
                mapOf("action" to action, "timestamp" to System.currentTimeMillis())
        )
    }

    /** Called when a notification is received. */
    fun onNotificationReceived(action: String = "received") {
        android.util.Log.d("AttentionSignalCollector", "onNotificationReceived: action=$action")

        // Only increment count for "received" action to avoid double counting
        if (action == "received") {
            notificationCount++
            notificationTimestamps.offer(System.currentTimeMillis())
            // Clean old timestamps (keep last 5 minutes)
            cleanOldNotificationTimestamps(System.currentTimeMillis(), 300000)
        }

        android.util.Log.d(
                "AttentionSignalCollector",
                "onNotificationReceived: emitting NOTIFICATION event, action=$action, notificationCount=$notificationCount"
        )
        emitEvent(
                BehaviorEventType.NOTIFICATION,
                mapOf("action" to action, "timestamp" to System.currentTimeMillis())
        )
    }

    /** Called when a notification is ignored. */
    fun onNotificationIgnored() {
        notificationIgnoredCount++
        // Emit "ignored" event without calling onNotificationReceived to avoid double counting
        emitEvent(
                BehaviorEventType.NOTIFICATION,
                mapOf("action" to "ignored", "timestamp" to System.currentTimeMillis())
        )
    }

    /** Called when a notification is opened. */
    fun onNotificationOpened() {
        // Emit "opened" event without calling onNotificationReceived to avoid double counting
        emitEvent(
                BehaviorEventType.NOTIFICATION,
                mapOf("action" to "opened", "timestamp" to System.currentTimeMillis())
        )
    }

    // Calculation methods
    private fun calculateAppSwitchesPerMinute(): Int {
        val currentTime = System.currentTimeMillis()
        return appSwitchTimestamps.count { it > currentTime - 60000 }
    }

    /** Stability index: 1.0 = very stable (few switches, long focus), 0.0 = very unstable. */
    private fun calculateStabilityIndex(): Double? {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        if (sessionDuration < 10000) return null // Need at least 10 seconds

        val switchRate = taskSwitchCount / (sessionDuration / 60000.0) // switches per minute
        val avgForegroundTime =
                if (taskSwitchCount > 0) {
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

    /** Fragmentation index: 1.0 = highly fragmented (many short idle gaps), 0.0 = continuous. */
    private fun calculateFragmentationIndex(): Double? {
        if (idleGaps.isEmpty()) return 0.0

        val totalGaps = idleGaps.size
        val shortGaps = idleGaps.count { it.duration < midIdleThreshold }
        val longGaps = idleGaps.count { it.duration >= midIdleThreshold }

        // More short gaps = higher fragmentation
        val fragmentationScore =
                if (totalGaps > 0) {
                    (shortGaps.toDouble() / totalGaps) * 0.7 +
                            (longGaps.toDouble() / totalGaps) * 1.0
                } else {
                    0.0
                }

        return minOf(1.0, fragmentationScore)
    }

    private fun emitEvent(type: BehaviorEventType, payload: Map<String, Any?>) {
        // Type is already Flutter-compatible (SCROLL, TAP, SWIPE, etc.)
        eventCallback?.invoke(
                BehaviorEvent(
                        sessionId = sessionId,
                        timestamp = java.time.Instant.now().toString(), // ISO 8601 format
                        eventType = type,
                        metrics = payload.filterValues { it != null }.mapValues { it.value as Any }
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
