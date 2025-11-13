package ai.synheart.behavior

import ai.synheart.behavior.internal.*
import android.app.Application
import android.content.Context
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Main entry point for the Synheart Behavioral SDK.
 *
 * This SDK collects digital behavioral signals from smartphones without
 * collecting any text, content, or PII - only timing-based signals.
 */
class SynheartBehavior private constructor(
    private val context: Context,
    private var config: BehaviorConfig
) {
    private val lock = ReentrantReadWriteLock()
    private var isInitialized = false
    private var isDisposed = false
    private var currentSessionTracker: SessionTracker? = null
    private var eventHandler: ((BehaviorEvent) -> Unit)? = null

    // Signal collectors
    private var inputCollector: InputSignalCollector? = null
    private var attentionCollector: AttentionSignalCollector? = null
    private var motionCollector: MotionSignalCollector? = null

    companion object {
        /**
         * Create and initialize a new instance of the SDK.
         */
        fun create(context: Context, config: BehaviorConfig = BehaviorConfig()): SynheartBehavior {
            return SynheartBehavior(context.applicationContext, config)
        }
    }

    /**
     * Initialize the SDK and start collecting behavioral signals.
     */
    fun initialize() = lock.write {
        if (isInitialized) {
            throw IllegalStateException("SDK already initialized")
        }
        if (isDisposed) {
            throw IllegalStateException("SDK has been disposed. Create a new instance.")
        }

        isInitialized = true
    }

    /**
     * Set event handler callback for receiving behavioral events.
     */
    fun setEventHandler(handler: (BehaviorEvent) -> Unit) = lock.write {
        this.eventHandler = handler
    }

    /**
     * Start a new behavioral tracking session.
     */
    fun startSession(sessionId: String? = null): String = lock.write {
        checkInitialized()

        // End any existing session first
        currentSessionTracker?.let {
            endSession(it.sessionId)
        }

        val sessionIdToUse = sessionId ?: generateSessionId()
        val tracker = SessionTracker(sessionIdToUse)
        currentSessionTracker = tracker

        // Create and start collectors for this session
        if (config.enableInputSignals) {
            inputCollector = InputSignalCollector(sessionIdToUse).apply {
                setEventCallback { event ->
                    handleEvent(event)
                    tracker.recordEvent(event)
                }
                start()
            }
        }

        if (config.enableAttentionSignals) {
            val app = context as? Application ?: throw IllegalStateException(
                "Context must be Application context. Use context.applicationContext when creating SDK."
            )
            attentionCollector = AttentionSignalCollector(sessionIdToUse, app, config.maxIdleGapSeconds).apply {
                setEventCallback { event ->
                    handleEvent(event)
                    tracker.recordEvent(event)
                }
                start()
            }
        }

        if (config.enableMotionLite) {
            motionCollector = MotionSignalCollector(sessionIdToUse, context).apply {
                setEventCallback { event ->
                    handleEvent(event)
                    tracker.recordEvent(event)
                }
                start()
            }
        }

        return sessionIdToUse
    }

    /**
     * End a session and return summary.
     */
    fun endSession(sessionId: String): BehaviorSessionSummary = lock.write {
        checkInitialized()

        val tracker = currentSessionTracker
            ?: throw IllegalStateException("No active session found")

        if (tracker.sessionId != sessionId) {
            throw IllegalArgumentException(
                "Session ID mismatch: active=${tracker.sessionId}, requested=$sessionId"
            )
        }

        // Stop all collectors
        inputCollector?.stop()
        attentionCollector?.stop()
        motionCollector?.stop()

        // Get summary from all collectors
        val inputSummary = inputCollector?.getSessionSummary() ?: emptyMap()
        val attentionSummary = attentionCollector?.getSessionSummary() ?: emptyMap()
        val motionSummary = motionCollector?.getSessionSummary() ?: emptyMap()

        val summary = tracker.getSessionSummary(inputSummary, attentionSummary, motionSummary)

        // Clean up
        inputCollector = null
        attentionCollector = null
        motionCollector = null
        currentSessionTracker = null

        return summary
    }

    /**
     * Get current rolling statistics snapshot.
     */
    fun getCurrentStats(): BehaviorStats = lock.read {
        checkInitialized()

        val tracker = currentSessionTracker
            ?: throw IllegalStateException("No active session. Call startSession() first.")

        val inputStats = inputCollector?.getCurrentStats() ?: emptyMap()
        val attentionStats = attentionCollector?.getCurrentStats() ?: emptyMap()
        val motionStats = motionCollector?.getCurrentStats() ?: emptyMap()

        return tracker.getCurrentStats(inputStats, attentionStats, motionStats)
    }

    /**
     * Update configuration at runtime.
     */
    fun updateConfig(newConfig: BehaviorConfig) = lock.write {
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
     * Dispose of the SDK instance and clean up resources.
     */
    fun dispose() = lock.write {
        if (isDisposed) return@write

        // End active session if any
        currentSessionTracker?.let {
            try {
                endSession(it.sessionId)
            } catch (e: Exception) {
                // Ignore errors during disposal
            }
        }

        currentSessionTracker = null
        isInitialized = false
        isDisposed = true
        eventHandler = null
    }

    /**
     * Report user activity to collectors (for idle gap tracking).
     * This should be called from your app's touch event handlers.
     */
    fun reportUserActivity() = lock.read {
        if (!isInitialized || isDisposed) return@read
        attentionCollector?.onUserActivity()
    }

    /**
     * Report a key event to the input collector.
     * This can be called from your app's EditText listeners or AccessibilityService.
     */
    fun reportKeyEvent() = lock.read {
        if (!isInitialized || isDisposed) return@read
        inputCollector?.onKeyEvent()
        attentionCollector?.onUserActivity()
    }

    /**
     * Report a scroll event to the input collector.
     * This can be called from your app's ScrollView or RecyclerView listeners.
     */
    fun reportScrollEvent(deltaY: Float) = lock.read {
        if (!isInitialized || isDisposed) return@read
        inputCollector?.onScrollEvent(deltaY)
        attentionCollector?.onUserActivity()
    }

    /**
     * Report a tap event to the input collector.
     */
    fun reportTapEvent() = lock.read {
        if (!isInitialized || isDisposed) return@read
        inputCollector?.onTapEvent()
        attentionCollector?.onUserActivity()
    }

    /**
     * Report a long press event to the input collector.
     */
    fun reportLongPressEvent() = lock.read {
        if (!isInitialized || isDisposed) return@read
        inputCollector?.onLongPressEvent()
        attentionCollector?.onUserActivity()
    }

    /**
     * Report a drag event to the input collector.
     */
    fun reportDragEvent(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) = lock.read {
        if (!isInitialized || isDisposed) return@read
        inputCollector?.onDragEvent(startX, startY, endX, endY, duration)
        attentionCollector?.onUserActivity()
    }

    private fun generateSessionId(): String {
        val prefix = config.sessionIdPrefix ?: "SESS"
        return "$prefix-${System.currentTimeMillis()}"
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("SDK not initialized. Call initialize() first.")
        }
        if (isDisposed) {
            throw IllegalStateException("SDK has been disposed. Create a new instance.")
        }
    }

    private fun handleEvent(event: BehaviorEvent) {
        eventHandler?.invoke(event)
    }
}

