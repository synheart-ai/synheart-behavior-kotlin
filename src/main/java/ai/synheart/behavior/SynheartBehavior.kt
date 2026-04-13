package ai.synheart.behavior

import ai.synheart.behavior.internal.*
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Main entry point for the Synheart Behavioral SDK.
 *
 * This SDK collects digital behavioral signals from smartphones without collecting any text,
 * content, or PII - only timing-based signals.
 */
class SynheartBehavior
private constructor(private val context: Context, private var config: BehaviorConfig) {
    private val lock = ReentrantReadWriteLock()
    private var _isInitialized = false
    private var isDisposed = false
    private var currentSessionTracker: SessionTracker? = null
    // Store ended sessions for raw event access after session end
    private val endedSessions = java.util.concurrent.ConcurrentHashMap<String, SessionTracker>()
    private var lastAppUseTime: Long? = null // For session spacing calculation
    private var eventHandler: ((BehaviorEvent) -> Unit)? = null

    // Signal collectors
    private var inputCollector: InputSignalCollector? = null
    private var attentionCollector: AttentionSignalCollector? = null
    private var gestureCollector: GestureCollector? = null
    private var motionCollector: MotionSignalCollector? = null

    // Motion state inference
    private val motionStateInference = MotionStateInference(context)

    // Window features (commented out - not needed for real-time event tracking)
    // private val windowAggregator = WindowAggregator()
    // private val featureExtractor = BehaviorFeatureExtractor()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Auto-end session after 1 minute in background
    private var backgroundTimer: Job? = null
    private val lifecycleObserver =
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_STOP -> onAppBackgrounded()
                        Lifecycle.Event.ON_START -> onAppForegrounded()
                        else -> {}
                    }
                }
            }

    // Flow channel for events (matching Flutter SDK's Stream<BehaviorEvent>)
    private val eventFlow = MutableSharedFlow<BehaviorEvent>(replay = 0, extraBufferCapacity = 64)

    // Flow channels for window features (commented out)
    // private val shortWindowFlow = MutableSharedFlow<BehaviorWindowFeatures>(replay = 1)
    // private val longWindowFlow = MutableSharedFlow<BehaviorWindowFeatures>(replay = 1)

    // Window feature update jobs (commented out)
    // private var shortWindowJob: Job? = null
    // private var longWindowJob: Job? = null

    companion object {
        /** Create and initialize a new instance of the SDK. */
        fun create(context: Context, config: BehaviorConfig = BehaviorConfig()): SynheartBehavior {
            return SynheartBehavior(context.applicationContext, config)
        }
    }

    /** Initialize the SDK and start collecting behavioral signals. */
    fun initialize() =
            lock.write {
                if (_isInitialized) {
                    throw IllegalStateException("SDK already initialized")
                }
                if (isDisposed) {
                    throw IllegalStateException("SDK has been disposed. Create a new instance.")
                }

                // Register with Notification Listener Service early (before session starts)
                // This allows the service to connect and be ready when sessions start
                if (config.enableAttentionSignals) {
                    // Verify we have Application context
                    if (context !is Application) {
                        throw IllegalStateException(
                                "Context must be Application context. Use context.applicationContext when creating SDK."
                        )
                    }
                    SynheartNotificationListenerService.setBehavior(this)
                }

                // Load motion state inference model if motion is enabled
                if (config.enableMotionLite) {
                    scope.launch {
                        try {
                            motionStateInference.loadModel()
                        } catch (e: Exception) {
                            android.util.Log.w(
                                    "SynheartBehavior",
                                    "Failed to load motion state inference model: ${e.message}"
                            )
                            // Continue initialization even if model loading fails
                        }
                    }
                }

                // Register lifecycle observer for auto-ending sessions after 1 minute in background
                ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

                _isInitialized = true
            }

    /** Set event handler callback for receiving behavioral events. */
    fun setEventHandler(handler: (BehaviorEvent) -> Unit) =
            lock.write { this.eventHandler = handler }

    /**
     * Flow of behavioral events emitted by the SDK. Subscribe to this flow to receive real-time
     * behavioral signals. Matches Flutter SDK's `onEvent` Stream property.
     */
    val onEvent: Flow<BehaviorEvent> = eventFlow.asSharedFlow()

    /** Check if the SDK is currently initialized. */
    val isInitialized: Boolean
        get() = lock.read { _isInitialized }

    /** Get the current active session ID, if any. */
    val currentSessionId: String?
        get() = lock.read { currentSessionTracker?.sessionId }

    // Track views to auto-attach collectors when sessions start
    private val attachedViews =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
            )

    /** Connect collectors to all tracked views. */
    private fun bindCollectorsToViews() {
        // Iterate safely over weak references
        val iterator = attachedViews.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view != null) {
                inputCollector?.attachToView(view)
                gestureCollector?.attachToView(view)
            }
        }
    }

    /** Start a new behavioral tracking session. Returns a BehaviorSession object. */
    fun startSession(sessionId: String? = null): BehaviorSession =
            lock.write {
                checkInitialized()

                // End any existing session first
                // Clear ended sessions when starting a new one (matching Flutter SDK behavior)
                currentSessionTracker?.let {
                    try {
                        endSession(it.sessionId)
                    } catch (e: Exception) {
                        // Ignore errors when ending previous session
                    }
                }
                // Clear ended sessions when starting a new session (matching Flutter SDK)
                endedSessions.clear()

                val sessionIdToUse = sessionId ?: generateSessionId()

                // Calculate session spacing (time between end of previous session and start of
                // current session)
                val now = System.currentTimeMillis()
                val sessionSpacing =
                        if (lastAppUseTime != null) {
                            now - lastAppUseTime!!
                        } else {
                            0L
                        }

                val tracker = SessionTracker(sessionIdToUse, context, sessionSpacing)
                currentSessionTracker = tracker

                // Create and start collectors for this session
                if (config.enableInputSignals) {
                    inputCollector =
                            InputSignalCollector(config, sessionIdToUse).apply {
                                setEventCallback { event ->
                                    handleEvent(event)
                                    tracker.recordEvent(event)
                                }
                                start()
                            }
                    gestureCollector =
                            GestureCollector(config, sessionIdToUse).apply {
                                setEventCallback { event ->
                                    handleEvent(event)
                                    tracker.recordEvent(event)
                                }
                                start()
                            }
                }

                if (config.enableAttentionSignals) {
                    val app =
                            context as? Application
                                    ?: throw IllegalStateException(
                                            "Context must be Application context. Use context.applicationContext when creating SDK."
                                    )

                    // Notification Listener Service should already be registered during
                    // initialize()
                    // But ensure it's set in case initialize() wasn't called or was called without
                    // attention signals
                    if (SynheartNotificationListenerService.isServiceConnected()) {
                        android.util.Log.d(
                                "SynheartBehavior",
                                "NotificationListenerService is connected and ready"
                        )
                    } else {
                        android.util.Log.w(
                                "SynheartBehavior",
                                "NotificationListenerService not connected yet. Make sure notification access is enabled in settings."
                        )
                    }

                    attentionCollector =
                            AttentionSignalCollector(sessionIdToUse, app, config.maxIdleGapSeconds)
                                    .apply {
                                        setEventCallback { event ->
                                            handleEvent(event)
                                            tracker.recordEvent(event)
                                        }
                                        start()
                                    }
                }

                // Create and start motion collector if enabled
                if (config.enableMotionLite) {
                    motionCollector = MotionSignalCollector(context, config)
                    val startTimestamp = System.currentTimeMillis()
                    motionCollector?.startSession(startTimestamp)
                }

                // Bind collectors to any previously attached views
                bindCollectorsToViews()

                // Start window feature streaming (commented out - not needed for real-time event
                // tracking)
                // startWindowFeatureStreaming()

                val startTimestamp = System.currentTimeMillis()
                return BehaviorSession(
                        sessionId = sessionIdToUse,
                        startTimestamp = startTimestamp,
                        endCallback = { id -> endSession(id) }
                )
            }

    /** End a session and return summary. */
    fun endSession(sessionId: String): BehaviorSessionSummary =
            lock.write {
                checkInitialized()

                val tracker =
                        currentSessionTracker
                                ?: throw IllegalStateException("No active session found")

                if (tracker.sessionId != sessionId) {
                    throw IllegalArgumentException(
                            "Session ID mismatch: active=${tracker.sessionId}, requested=$sessionId"
                    )
                }

                // Stop all collectors
                inputCollector?.stop()
                attentionCollector?.stop()
                gestureCollector?.stop()

                // Stop motion collector and get motion data
                val motionData =
                        motionCollector?.stopSession()
                                ?: emptyList<MotionSignalCollector.MotionDataPoint>()

                android.util.Log.d(
                        "SynheartBehavior",
                        "Motion data collected: ${motionData.size} windows, enableMotionLite=${config.enableMotionLite}"
                )

                // Stop window feature streaming (commented out)
                // stopWindowFeatureStreaming()

                // Sync app switch count from AttentionSignalCollector before ending session
                // (matching Flutter SDK)
                attentionCollector?.let { collector ->
                    val currentAppSwitchCount = collector.getAppSwitchCount()
                    if (currentAppSwitchCount > 0) {
                        tracker.updateAppSwitchCount(currentAppSwitchCount)
                    }
                }

                // Get summary from all collectors
                val inputSummary = inputCollector?.getSessionSummary() ?: emptyMap()
                val attentionSummary = attentionCollector?.getSessionSummary() ?: emptyMap()
                val gestureSummary = gestureCollector?.getSessionSummary() ?: emptyMap()

                // Convert motion data to SDK MotionDataPoint format
                val motionDataForSummary: List<MotionDataPoint>? =
                        if (config.enableMotionLite && motionData.isNotEmpty()) {
                            val converted =
                                    motionData.map { dataPoint ->
                                        MotionDataPoint(
                                                timestamp = dataPoint.timestamp,
                                                features = dataPoint.features
                                        )
                                    }
                            android.util.Log.d(
                                    "SynheartBehavior",
                                    "Converted ${converted.size} motion data points for summary"
                            )
                            converted
                        } else {
                            android.util.Log.d(
                                    "SynheartBehavior",
                                    "Motion data not included: enableMotionLite=${config.enableMotionLite}, motionData.size=${motionData.size}"
                            )
                            null
                        }

                // Get summary (motion state will be added if motion data is available)
                var summary =
                        tracker.getSessionSummary(
                                inputSummary,
                                attentionSummary,
                                gestureSummary,
                                motionDataForSummary
                        )

                // Update lastAppUseTime to session end time for next session's spacing calculation
                // Session spacing = time between end_session and start_session
                val sessionEndTime = System.currentTimeMillis()
                lastAppUseTime = sessionEndTime

                // Run motion state inference if motion data is available (using runBlocking for
                // synchronous call)
                if (motionDataForSummary != null &&
                                motionDataForSummary.isNotEmpty() &&
                                config.enableMotionLite
                ) {
                    if (!motionStateInference.getIsLoaded()) {
                        try {
                            runBlocking { motionStateInference.loadModel() }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                    "SynheartBehavior",
                                    "Failed to load motion state model: ${e.message}"
                            )
                        }
                    }

                    if (motionStateInference.getIsLoaded()) {
                        try {
                            android.util.Log.d(
                                    "SynheartBehavior",
                                    "Running motion state inference on ${motionDataForSummary.size} data points"
                            )
                            val motionState = runBlocking {
                                motionStateInference.inferMotionState(motionDataForSummary)
                            }
                            android.util.Log.d(
                                    "SynheartBehavior",
                                    "Motion state inference completed: majorState=${motionState.majorState}, confidence=${motionState.confidence}, stateCount=${motionState.state.size}"
                            )
                            // Update summary with motion state
                            summary = summary.copy(motionState = motionState)
                        } catch (e: Exception) {
                            android.util.Log.e(
                                    "SynheartBehavior",
                                    "Failed to run motion state inference: ${e.message}",
                                    e
                            )
                            // Continue without motion state if inference fails
                        }
                    } else {
                        android.util.Log.w(
                                "SynheartBehavior",
                                "Motion state inference model not loaded"
                        )
                    }
                }

                // Store ended session in map (don't remove - allows raw event access after
                // access it)
                // This matches Flutter SDK behavior where sessionData is kept until next session
                // starts
                endedSessions[tracker.sessionId] = tracker

                // Clean up collectors but keep tracker in endedSessions map
                inputCollector = null
                attentionCollector = null
                gestureCollector = null
                motionCollector = null
                currentSessionTracker = null
                // windowAggregator.clear()

                return summary
            }

    /** Get current rolling statistics snapshot. */
    fun getCurrentStats(): BehaviorStats =
            lock.read {
                checkInitialized()

                val tracker =
                        currentSessionTracker
                                ?: throw IllegalStateException(
                                        "No active session. Call startSession() first."
                                )

                val inputStats = inputCollector?.getCurrentStats() ?: emptyMap()
                val attentionStats = attentionCollector?.getCurrentStats() ?: emptyMap()
                val gestureStats = gestureCollector?.getCurrentStats() ?: emptyMap()

                return tracker.getCurrentStats(inputStats, attentionStats, gestureStats)
            }

    /** Update configuration at runtime. */
    fun updateConfig(newConfig: BehaviorConfig) =
            lock.write {
                checkInitialized()

                val hasActiveSession = currentSessionTracker != null
                if (hasActiveSession) {
                    throw IllegalStateException(
                            "Cannot update config while session is active. End the session first."
                    )
                }

                config = newConfig
            }

    /**
     * Attach collectors to a view to automatically track user interactions. This is the preferred
     * way to collect input and gesture signals.
     */
    fun attachToView(view: View) =
            lock.write { // Write lock because we modify attachedViews
                if (!_isInitialized || isDisposed) return@write

                // Track the view so we can re-attach on session restart
                attachedViews.add(view)

                // Attach to current collectors if active
                inputCollector?.attachToView(view)
                gestureCollector?.attachToView(view)
            }

    /** Dispose of the SDK instance and clean up resources. */
    fun dispose() =
            lock.write {
                if (isDisposed) return@write

                // Cancel background timer
                backgroundTimer?.cancel()
                backgroundTimer = null

                // Remove lifecycle observer
                ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)

                // End active session if any
                currentSessionTracker?.let {
                    try {
                        endSession(it.sessionId)
                    } catch (e: Exception) {
                        // Ignore errors during disposal
                    }
                }

                SynheartNotificationListenerService.setBehavior(null)

                currentSessionTracker = null
                _isInitialized = false
                isDisposed = true
                eventHandler = null

                attachedViews.clear()

                // Stop window feature streaming and clean up (commented out)
                // stopWindowFeatureStreaming()
                // windowAggregator.clear()

                // Dispose motion state inference
                motionStateInference.dispose()

                scope.cancel()
            }

    /**
     * Report user activity to collectors (for idle gap tracking). This should be called from your
     * app's touch event handlers if not using attachToView.
     */
    fun reportUserActivity() =
            lock.read {
                if (!_isInitialized || isDisposed) return@read
                attentionCollector?.onUserActivity()
            }

    /** Report a notification event. */
    fun reportNotification(action: String = "received") =
            lock.read {
                if (!_isInitialized || isDisposed) {
                    android.util.Log.w(
                            "SynheartBehavior",
                            "reportNotification: SDK not initialized or disposed, action=$action"
                    )
                    return@read
                }
                if (attentionCollector == null) {
                    android.util.Log.w(
                            "SynheartBehavior",
                            "reportNotification: attentionCollector is null (no active session?), action=$action"
                    )
                    return@read
                }
                android.util.Log.d(
                        "SynheartBehavior",
                        "reportNotification: reporting notification action=$action"
                )
                attentionCollector?.onNotificationReceived(action)
            }

    /** Report a notification ignored event. */
    fun reportNotificationIgnored() =
            lock.read {
                if (!_isInitialized || isDisposed) return@read
                attentionCollector?.onNotificationIgnored()
            }

    /** Report a notification opened event. */
    fun reportNotificationOpened() =
            lock.read {
                if (!_isInitialized || isDisposed) return@read
                attentionCollector?.onNotificationOpened()
            }

    /**
     * Reinitialize phone state listener after permission is granted. Call this after the user
     * grants READ_PHONE_STATE permission.
     */
    fun reinitializePhoneStateListener() =
            lock.read {
                if (!_isInitialized || isDisposed) return@read
                attentionCollector?.reinitializePhoneStateListener()
            }

    /**
     * Hot restart collectors during an active session (similar to Flutter's hot restart). This
     * restarts collectors to pick up permission changes or configuration updates without ending the
     * current session.
     *
     * Use this when:
     * - Permissions are granted during a session
     * - You want to refresh collector state
     * - Configuration changes need to take effect immediately
     */
    fun hotRestartCollectors() =
            lock.write {
                if (!_isInitialized || isDisposed) return@write
                val tracker =
                        currentSessionTracker
                                ?: throw IllegalStateException(
                                        "No active session found. Cannot hot restart without an active session."
                                )

                val sessionId = tracker.sessionId

                // Restart attention collector (includes phone state listener)
                if (config.enableAttentionSignals) {
                    val app =
                            context as? Application
                                    ?: throw IllegalStateException(
                                            "Context must be Application context."
                                    )

                    // Stop existing collector
                    val prevCount = attentionCollector?.getAppSwitchCount() ?: 0
                    attentionCollector?.stop()

                    // Create and start new collector with same session ID and preserved count
                    attentionCollector =
                            AttentionSignalCollector(
                                            sessionId,
                                            app,
                                            config.maxIdleGapSeconds,
                                            prevCount
                                    )
                                    .apply {
                                        setEventCallback { event ->
                                            handleEvent(event)
                                            tracker.recordEvent(event)
                                        }
                                        start()
                                    }
                }

                // Restart gesture collector if config changed
                if (config.enableInputSignals) {
                    gestureCollector?.stop()
                    inputCollector?.stop()

                    inputCollector =
                            InputSignalCollector(config, sessionId).apply {
                                setEventCallback { event ->
                                    handleEvent(event)
                                    tracker.recordEvent(event)
                                }
                                start()
                            }
                    gestureCollector =
                            GestureCollector(config, sessionId).apply {
                                setEventCallback { event ->
                                    handleEvent(event)
                                    tracker.recordEvent(event)
                                }
                                start()
                            }

                    // Re-bind to views
                    bindCollectorsToViews()
                }

                // Note: Input collector doesn't need restart as it doesn't depend on permissions
                // BUT if we modified config, we might need to. Added explicit restart logic above.
            }

    private fun generateSessionId(): String {
        val prefix = config.sessionIdPrefix ?: "SESS"
        return "$prefix-${System.currentTimeMillis()}"
    }

    private fun checkInitialized() {
        if (!_isInitialized) {
            throw IllegalStateException("SDK not initialized. Call initialize() first.")
        }
        if (isDisposed) {
            throw IllegalStateException("SDK has been disposed. Create a new instance.")
        }
    }

    private fun handleEvent(event: BehaviorEvent) {
        eventHandler?.invoke(event)
        // Emit to Flow (matching Flutter SDK's Stream behavior)
        eventFlow.tryEmit(event)
        // Add to window aggregator for feature extraction (commented out)
        // windowAggregator.addEvent(event)
    }

    /**
     * Get window features for a specific window type. (Commented out - not needed for real-time
     * event tracking)
     */
    // fun getWindowFeatures(windowType: WindowType): BehaviorWindowFeatures? =
    //         lock.read {
    //             if (!_isInitialized || isDisposed) return null
    //
    //             val events = windowAggregator.getWindowEvents(windowType)
    //             val windowDurationMs = windowAggregator.getWindowDurationMs(windowType)
    //             return featureExtractor.extractFeatures(events, windowType, windowDurationMs)
    //         }

    /**
     * Flow of short window features (30-second window, updates every 5 seconds). (Commented out)
     */
    // val onShortWindowFeatures: Flow<BehaviorWindowFeatures> = shortWindowFlow.asSharedFlow()

    /** Flow of long window features (5-minute window, updates every 30 seconds). (Commented out) */
    // val onLongWindowFeatures: Flow<BehaviorWindowFeatures> = longWindowFlow.asSharedFlow()

    /**
     * Start window feature streaming. This is automatically called when a session starts.
     * (Commented out)
     */
    // private fun startWindowFeatureStreaming() {
    //     // Start short window updates (every 5 seconds)
    //     shortWindowJob =
    //             scope.launch {
    //                 while (isActive && isInitialized && !isDisposed) {
    //                     delay(5000) // Update every 5 seconds
    //                     val features = getWindowFeatures(WindowType.SHORT)
    //                     features?.let { shortWindowFlow.emit(it) }
    //                 }
    //             }
    //
    //     // Start long window updates (every 30 seconds)
    //     longWindowJob =
    //             scope.launch {
    //                 while (isActive && isInitialized && !isDisposed) {
    //                     delay(30000) // Update every 30 seconds
    //                     val features = getWindowFeatures(WindowType.LONG)
    //                     features?.let { longWindowFlow.emit(it) }
    //                 }
    //             }
    // }

    /** Stop window feature streaming. (Commented out) */
    // private fun stopWindowFeatureStreaming() {
    //     shortWindowJob?.cancel()
    //     longWindowJob?.cancel()
    //     shortWindowJob = null
    //     longWindowJob = null
    // }

    /**
     * Check if notification listener service is enabled. This checks if the user has granted
     * notification access in system settings. This is different from POST_NOTIFICATIONS permission.
     */
    fun checkNotificationListenerEnabled(): Boolean {
        val packageNames =
                android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        "enabled_notification_listeners"
                )
                        ?: return false

        val packageName = context.packageName
        return packageNames.contains(packageName)
    }

    /**
     * Check if the notification listener service is currently connected and active. This is
     * different from checkNotificationListenerEnabled() - even if enabled in settings, the service
     * might not be connected yet.
     */
    fun checkNotificationListenerConnected(): Boolean {
        return SynheartNotificationListenerService.isServiceConnected()
    }

    /**
     * Open system settings to enable notification listener service. This will take the user to the
     * notification access settings screen.
     */
    fun requestNotificationListenerAccess() {
        val intent =
                android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Check if notification permission is granted. On Android 13+ (API 33+), requires
     * POST_NOTIFICATIONS permission.
     */
    fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        // On older versions, notification permission is granted by default
        return true
    }

    /**
     * Request notification permission. Note: This method doesn't actually request permission - you
     * need to call Activity.requestPermissions() or use
     * ActivityResultContracts.RequestPermission(). This is a placeholder that returns false to
     * indicate permission needs to be requested.
     */
    fun requestNotificationPermission(): Boolean {
        // In a real implementation, this would trigger a permission request
        // For now, return false to indicate permission is not granted
        return checkNotificationPermission()
    }

    /** Check if call/phone state permission is granted. */
    fun checkCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request call/phone state permission. Note: This method doesn't actually request permission -
     * you need to call Activity.requestPermissions() or use
     * ActivityResultContracts.RequestPermission(). This is a placeholder that returns false to
     * indicate permission needs to be requested.
     */
    fun requestCallPermission(): Boolean {
        // In a real implementation, this would trigger a permission request
        // For now, return false to indicate permission is not granted
        return checkCallPermission()
    }

    /**
     * Send an event to the SDK. This allows manual event reporting for custom tracking scenarios.
     * Only predefined event types are supported (scroll, tap, swipe, notification, call, typing).
     */
    fun sendEvent(event: BehaviorEvent) =
            lock.read {
                checkInitialized()
                handleEvent(event)
                currentSessionTracker?.recordEvent(event)
            }

    /**
     * Called when the app goes to background. Starts a timer to auto-end the session after 1 minute
     * if still in background.
     */
    private fun onAppBackgrounded() {
        lock.read {
            if (!_isInitialized || isDisposed) return@read
            if (currentSessionTracker == null) return@read

            // Cancel any existing timer
            backgroundTimer?.cancel()

            // Start timer to auto-end session after 1 minute
            backgroundTimer =
                    scope.launch {
                        delay(60000) // 1 minute
                        lock.write {
                            // Check again if session is still active
                            if (currentSessionTracker != null && !isDisposed) {
                                android.util.Log.d(
                                        "SynheartBehavior",
                                        "App has been in background for 1 minute. Auto-ending session..."
                                )
                                try {
                                    val sessionId = currentSessionTracker!!.sessionId
                                    endSession(sessionId)
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "SynheartBehavior",
                                            "Error auto-ending session: ${e.message}",
                                            e
                                    )
                                }
                            }
                        }
                    }
        }
    }

    /**
     * Called when the app returns to foreground. Cancels the auto-end timer if app returned before
     * 1 minute.
     */
    private fun onAppForegrounded() {
        lock.read {
            if (!_isInitialized || isDisposed) return@read

            // Cancel background timer if app returned before timeout
            backgroundTimer?.cancel()
            backgroundTimer = null
        }
    }
}
