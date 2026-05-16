package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.BehaviorEvent
import android.view.View

/**
 * Collects input signals. Note: Typing/keystroke tracking has been removed. Text field interactions
 * are captured as tap events by GestureCollector.
 */
internal class InputSignalCollector(
        private var config: BehaviorConfig,
        private val sessionId: String
) : SignalCollector {

    private var eventHandler: ((BehaviorEvent) -> Unit)? = null

    override fun start() {
        // No-op
    }

    override fun stop() {
        // No-op
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventHandler = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        return emptyMap()
    }

    override fun getSessionSummary(): Map<String, Any?> {
        return emptyMap()
    }

    override fun resetSession() {
        stop()
    }

    @Suppress("UNUSED_PARAMETER")
    fun attachToView(view: View) {
        if (!config.enableInputSignals) {
            android.util.Log.d("InputSignalCollector", "Input signals disabled, not attaching")
            return
        }
        // Keystroke tracking removed; text field interactions are tap events from GestureCollector.
    }

    fun updateConfig(newConfig: BehaviorConfig) {
        config = newConfig
    }

    fun dispose() {
        // No-op
    }
}
