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
    // Keystroke tracking removed - text field interactions are captured as tap events
    // private val keystrokeTimestamps = LinkedList<Long>()
    // private var lastKeystrokeTime: Long = 0
    // private var currentBurstLength = 0
    // private val maxBurstGap = 2000L // 2 seconds between keystrokes to be in same burst

    // Text watcher removed - keystroke tracking disabled
    // private val textWatcher =
    //         object : TextWatcher {
    //             override fun beforeTextChanged(
    //                     s: CharSequence?,
    //                     start: Int,
    //                     count: Int,
    //                     after: Int
    //             ) {}
    //
    //             override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)
    // {
    //                 if (count > 0) { // Character added
    //                     onKeystroke()
    //                 }
    //             }
    //
    //             override fun afterTextChanged(s: Editable?) {}
    //         }

    override fun start() {
        // No-op
    }

    override fun stop() {
        // keystrokeTimestamps.clear()
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventHandler = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        // Placeholder for parity
        return emptyMap()
    }

    override fun getSessionSummary(): Map<String, Any?> {
        // Placeholder for parity
        return emptyMap()
    }

    override fun resetSession() {
        stop()
    }

    fun attachToView(view: View) {
        // Keystroke tracking removed - text field interactions are captured as tap events by
        // GestureCollector
        // No-op: InputSignalCollector no longer tracks keystrokes
        if (!config.enableInputSignals) {
            android.util.Log.d("InputSignalCollector", "Input signals disabled, not attaching")
            return
        }

        // Text watcher attachment removed - keystroke tracking disabled
        // android.util.Log.d(
        //         "InputSignalCollector",
        //         "Attaching to view: ${view.javaClass.simpleName}"
        // )
        // when (view) {
        //     is EditText -> {
        //         android.util.Log.d("InputSignalCollector", "Found EditText, attaching text
        // watcher")
        //         view.addTextChangedListener(textWatcher)
        //     }
        //     is ViewGroup -> {
        //         android.util.Log.d(
        //                 "InputSignalCollector",
        //                 "Found ViewGroup with ${view.childCount} children, searching recursively"
        //         )
        //         // Recursively attach to all EditText children
        //         for (i in 0 until view.childCount) {
        //             attachToView(view.getChildAt(i))
        //         }
        //     }
        //     else -> {
        //         android.util.Log.d(
        //                 "InputSignalCollector",
        //                 "View is neither EditText nor ViewGroup, skipping"
        //         )
        //     }
        // }
    }

    fun updateConfig(newConfig: BehaviorConfig) {
        config = newConfig
    }

    // Keystroke tracking methods removed - text field interactions are captured as tap events by
    // GestureCollector
    // private fun onKeystroke() {
    //     android.util.Log.d("InputSignalCollector", "KEYSTROKE DETECTED!")
    //     val now = System.currentTimeMillis()
    //     keystrokeTimestamps.add(now)
    //
    //     // Keep only last 100 keystrokes
    //     while (keystrokeTimestamps.size > 100) {
    //         keystrokeTimestamps.removeFirst()
    //     }
    //
    //     // Check if part of current burst
    //     if (lastKeystrokeTime > 0 && (now - lastKeystrokeTime) < maxBurstGap) {
    //         currentBurstLength++
    //     } else {
    //         // New burst
    //         if (currentBurstLength > 0) {
    //             emitTypingBurst(currentBurstLength)
    //         }
    //         currentBurstLength = 1
    //     }
    //
    //     // Calculate inter-key latency and emit typing cadence
    //     // For first keystroke, use a default latency of 0 (will be counted but with 0 latency)
    //     if (lastKeystrokeTime > 0) {
    //         val latency = now - lastKeystrokeTime
    //         android.util.Log.d(
    //                 "InputSignalCollector",
    //                 "Emitting typingCadence event with latency=$latency"
    //         )
    //         emitTypingCadence(latency)
    //     } else {
    //         // First keystroke: emit with 0 latency so it's counted
    //         android.util.Log.d(
    //                 "InputSignalCollector",
    //                 "First keystroke, emitting typingCadence event with latency=0"
    //         )
    //         emitTypingCadence(0)
    //     }
    //
    //     lastKeystrokeTime = now
    // }

    // private fun getIsoTimestamp(): String {
    //     return Instant.now().toString()
    // }

    // private fun emitTypingCadence(interKeyLatency: Long) {
    //     // In new model, keystrokes are tracked as tap events
    //     // Estimate tap duration (typically 50-150ms for keyboard taps)
    //     val estimatedTapDuration = interKeyLatency.coerceIn(50, 150).toInt()
    //
    //     eventHandler?.invoke(
    //             BehaviorEvent(
    //                     sessionId = sessionId,
    //                     timestamp = getIsoTimestamp(),
    //                     eventType = BehaviorEventType.TAP,
    //                     metrics =
    //                             mapOf(
    //                                     "tap_duration_ms" to estimatedTapDuration,
    //                                     "long_press" to false
    //                             )
    //             )
    //     )
    // }

    // private fun emitTypingBurst(burstLength: Int) {
    //     // Bursts are now tracked as individual tap events
    //     // No need to emit separate burst events
    // }

    fun dispose() {
        // keystrokeTimestamps.clear()
    }
}
