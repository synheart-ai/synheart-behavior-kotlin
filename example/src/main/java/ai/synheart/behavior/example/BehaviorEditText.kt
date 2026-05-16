package ai.synheart.behavior.example

import ai.synheart.behavior.BehaviorEvent
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.sqrt

/**
 * EditText wrapper for behavior tracking. Tracks typing sessions from keyboard open (focus gain) to
 * keyboard close (focus loss). Calculates all typing metrics and emits a typing event when the
 * session ends. Matches Flutter SDK's BehaviorTextField implementation.
 */
class BehaviorEditText
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Typing session tracking
    private var sessionStartTime: Long? = null
    private var lastKeystrokeTime: Long? = null
    private var previousLength: Int = 0
    private val interKeyLatencies = mutableListOf<Int>() // Store latencies in milliseconds

    // Backspace and clipboard tracking (for correction_rate and clipboard_activity_rate)
    private var backspaceCount: Int = 0
    private var pasteCount: Int = 0
    private var copyCount: Int = 0
    private var cutCount: Int = 0
    // Selection/length before current change (for paste/cut/copy detection)
    private var prevSelStart: Int = 0
    private var prevSelEnd: Int = 0
    private var prevLenInBefore: Int = 0

    // Static list to track all active BehaviorEditText instances for session/app lifecycle handling
    companion object {
        private val activeInstances = mutableListOf<BehaviorEditText>()

        /**
         * Static method to end all active typing sessions. Called when session ends or app goes to
         * background.
         */
        fun endAllTypingSessions() {
            activeInstances.toList().forEach { it.endTypingSession() }
        }
    }

    // Constants from note-4.md
    private val gapThresholdMs = 5000 // 5 seconds for gap count
    private val activityThresholdMs = 2000 // 2 seconds for activity ratio
    private val deepTypingDurationSeconds = 60 // 1 minute
    private val vMax = 10.0 // taps/s for speed normalization
    private val w1 = 0.4 // weight for typing speed
    private val w2 = 0.35 // weight for gap behavior
    private val w3 = 0.25 // weight for cadence stability

    var onTypingEvent: ((BehaviorEvent) -> Unit)? = null
    var onClipboardEvent: ((BehaviorEvent) -> Unit)? = null

    private val textWatcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                ) {
                    prevLenInBefore = s?.length ?: 0
                    prevSelStart = selectionStart
                    prevSelEnd = selectionEnd
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onTextChanged()
                }
            }

    init {
        addTextChangedListener(textWatcher)
        setOnFocusChangeListener { _, hasFocus -> onFocusChanged(hasFocus) }
        previousLength = text?.length ?: 0
        // Register this instance for session/app lifecycle handling
        activeInstances.add(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // End any active session before detaching
        endTypingSession()
        // Unregister this instance
        activeInstances.remove(this)
    }

    /**
     * Detect Copy from the context menu (or floating toolbar). On Android, copy does not
     * change the text and often keeps the selection, so TextWatcher never fires. This override
     * runs when the user taps Copy. Cut is still detected in onTextChanged (deletion with selection).
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.copy) {
            if (sessionStartTime == null) startTypingSession()
            copyCount++
            emitClipboardEvent("copy")
        }
        return super.onTextContextMenuItem(id)
    }

    private fun onFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            // Keyboard opened - start typing session
            startTypingSession()
        } else {
            // Keyboard closed or focus lost - end typing session immediately
            endTypingSession()
        }
    }

    private fun startTypingSession() {
        // Always start fresh session when focus is gained
        sessionStartTime = System.currentTimeMillis()
        lastKeystrokeTime = null
        interKeyLatencies.clear()
        previousLength = text?.length ?: 0
        backspaceCount = 0
        pasteCount = 0
        copyCount = 0
        cutCount = 0
    }

    private fun endTypingSession() {
        // If we have an active session with at least one keystroke (or paste/backspace only), emit the event
        val hasKeystrokes = interKeyLatencies.isNotEmpty() || lastKeystrokeTime != null
        if (sessionStartTime != null && hasKeystrokes) {
            emitTypingSessionEvent()
        }

        // Always reset session state when focus is lost
        sessionStartTime = null
        lastKeystrokeTime = null
        interKeyLatencies.clear()
    }

    private fun onTextChanged() {
        // Only track keystrokes when text field has focus
        if (!hasFocus()) {
            previousLength = text?.length ?: 0
            return
        }

        val currentLength = text?.length ?: 0
        val now = System.currentTimeMillis()
        val lengthDiff = currentLength - previousLength

        // Paste detection: 2+ characters added in one change
        if (lengthDiff >= 2) {
            if (sessionStartTime == null) startTypingSession()
            pasteCount++
            emitClipboardEvent("paste")
            lastKeystrokeTime = now
            previousLength = currentLength
            return
        }

        // Copy detection: had selection, now collapsed, text unchanged
        if (lengthDiff == 0 && prevSelStart != prevSelEnd && selectionStart == selectionEnd) {
            copyCount++
            emitClipboardEvent("copy")
            previousLength = currentLength
            return
        }

        // Deletion: backspace vs cut
        if (currentLength < previousLength) {
            if (sessionStartTime == null) startTypingSession()
            val deleted = previousLength - currentLength
            val hadSelection = prevSelStart != prevSelEnd
            if (hadSelection) {
                cutCount++
                emitClipboardEvent("cut")
            } else {
                backspaceCount += deleted
            }
            previousLength = currentLength
            return
        }

        // Single character added (normal typing)
        if (lengthDiff == 1) {
            if (sessionStartTime == null) {
                startTypingSession()
            }
            if (lastKeystrokeTime != null) {
                val latency = (now - lastKeystrokeTime!!).toInt()
                interKeyLatencies.add(latency)
            }
            lastKeystrokeTime = now
        }

        previousLength = currentLength
    }

    private fun emitClipboardEvent(action: String) {
        val event = BehaviorEvent.clipboard(sessionId = "current", action = action, context = "textField")
        onClipboardEvent?.invoke(event)
    }

    private fun emitTypingSessionEvent() {
        if (sessionStartTime == null) return
        // Allow emitting with only backspace/paste (no keystrokes): typingTapCount can be 0
        val hasIntervals = interKeyLatencies.isNotEmpty()
        val typingTapCount = if (hasIntervals) interKeyLatencies.size + 1 else 0
        if (typingTapCount == 0 && backspaceCount == 0 && pasteCount == 0 && copyCount == 0 && cutCount == 0) {
            return
        }

        val sessionEndTime = lastKeystrokeTime ?: System.currentTimeMillis()
        val durationMs = sessionEndTime - sessionStartTime!!
        val durationSeconds = durationMs / 1000.0

        // N = typing_tap_count (total number of keyboard tap events); may be 0 if only paste/backspace
        val tapCount = if (interKeyLatencies.isNotEmpty()) interKeyLatencies.size + 1 else 0

        // typing_speed = N / T (taps per second)
        val typingSpeed = if (durationSeconds > 0) tapCount / durationSeconds else 0.0

        // mean_inter_tap_interval_ms = μ_Δt = (1 / (N - 1)) × Σ(i=2 to N) Δtᵢ
        var meanInterTapIntervalMs = 0.0
        if (interKeyLatencies.isNotEmpty()) {
            val sum = interKeyLatencies.sum()
            val intervalCount = if (tapCount > 1) tapCount - 1 else interKeyLatencies.size
            meanInterTapIntervalMs = if (intervalCount > 0) sum.toDouble() / intervalCount else 0.0
        }

        // typing_cadence_variability: Standard deviation of inter-tap intervals
        var typingCadenceVariability = 0.0
        if (tapCount >= 3 && interKeyLatencies.size >= 2 && meanInterTapIntervalMs > 0) {
            val nIntervals = interKeyLatencies.size
            val denominator = nIntervals - 1

            if (denominator > 0) {
                val sumSquaredDiffs =
                        interKeyLatencies.sumOf { latency ->
                            val diff = latency - meanInterTapIntervalMs
                            diff * diff
                        }
                val variance = sumSquaredDiffs / denominator
                typingCadenceVariability = sqrt(variance)
            }
        }

        // typing_cadence_stability = 1 - min(1, (σ_Δt / μ_Δt))
        var typingCadenceStability = 0.0
        if (meanInterTapIntervalMs > 0 && typingCadenceVariability > 0) {
            val cv = typingCadenceVariability / meanInterTapIntervalMs
            typingCadenceStability = (1.0 - min(1.0, cv)).coerceIn(0.0, 1.0)
        }

        // typing_gap_count = Σ(i=2 to N) 1(Δtᵢ > τ_gap) where τ_gap = 5 seconds
        val typingGapCount = interKeyLatencies.count { it > gapThresholdMs }

        // typing_gap_ratio = typing_gap_count / (N - 1)
        val intervalCountForGap = if (tapCount > 1) tapCount - 1 else interKeyLatencies.size
        val typingGapRatio =
                if (intervalCountForGap > 0) typingGapCount.toDouble() / intervalCountForGap else 0.0

        // typing_burstiness
        var typingBurstiness = 0.0
        if (interKeyLatencies.size > 1 && meanInterTapIntervalMs > 0 && typingCadenceVariability > 0
        ) {
            val bRaw =
                    (typingCadenceVariability - meanInterTapIntervalMs) /
                            (typingCadenceVariability + meanInterTapIntervalMs)
            typingBurstiness = ((bRaw + 1.0) / 2.0).coerceIn(0.0, 1.0)
        }

        // typing_activity_ratio = (Σ(i=2 to N) min(Δtᵢ, τ_activity)) / T
        val activeTypingTime = interKeyLatencies.sumOf { min(it, activityThresholdMs) }
        val typingActivityRatio =
                if (durationMs > 0) (activeTypingTime.toDouble() / durationMs).coerceIn(0.0, 1.0)
                else 0.0

        // typing_speed_norm = min(1, (typing_speed / v_max))
        val typingSpeedNorm = min(1.0, typingSpeed / vMax)

        // typing_interaction_intensity = w₁ · typing_speed_norm + w₂ · (1 - typing_gap_ratio) + w₃
        // · typing_cadence_stability
        val typingInteractionIntensity =
                (w1 * typingSpeedNorm + w2 * (1.0 - typingGapRatio) + w3 * typingCadenceStability)
                        .coerceIn(0.0, 1.0)

        // deep_typing = true if duration ≥ 60 seconds (1 minute)
        val deepTyping = durationSeconds >= deepTypingDurationSeconds

        // Create typing session event with all calculated metrics
        val isoFormatter = DateTimeFormatter.ISO_INSTANT
        val startAt = isoFormatter.format(Instant.ofEpochMilli(sessionStartTime!!))
        val endAt = isoFormatter.format(Instant.ofEpochMilli(sessionEndTime))

        val typingEvent =
                BehaviorEvent.typing(
                        sessionId = "current", // Will be replaced with actual session ID by SDK
                        typingTapCount = tapCount,
                        typingSpeed = typingSpeed,
                        meanInterTapIntervalMs = meanInterTapIntervalMs,
                        typingCadenceVariability = typingCadenceVariability,
                        typingCadenceStability = typingCadenceStability,
                        typingGapCount = typingGapCount,
                        typingGapRatio = typingGapRatio,
                        typingBurstiness = typingBurstiness,
                        typingActivityRatio = typingActivityRatio,
                        typingInteractionIntensity = typingInteractionIntensity,
                        durationSeconds = durationSeconds.toInt(),
                        startAt = startAt,
                        endAt = endAt,
                        deepTyping = deepTyping,
                        backspaceCount = backspaceCount,
                        numberOfCopy = copyCount,
                        numberOfPaste = pasteCount,
                        numberOfCut = cutCount
                )

        // Emit event through callback
        onTypingEvent?.invoke(typingEvent)
    }
}
