package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collects gesture and scroll signals. Privacy: Only timing and velocity metrics, no content or
 * coordinates.
 */
internal class GestureCollector(private var config: BehaviorConfig, private val sessionId: String) :
        SignalCollector {

    private var eventCallback: ((BehaviorEvent) -> Unit)? = null

    // Scroll tracking - using native scroll velocity
    private var scrollStartTime: Long? = null
    private var scrollStartPosition = 0
    private var scrollEndPosition = 0
    private var lastScrollPosition = 0
    private var lastScrollY = 0 // Keep for delta calculation
    private var lastScrollTime = 0L
    private var previousVelocity = 0.0
    private var lastScrollDirection: String? = null // "up", "down", "left", "right"
    private var hasDirectionReversal = false
    private var scrollStopTimer: android.os.Handler? = null
    private val scrollStopThresholdMs = 1200L // Wait 1200ms after scroll stops before emitting
    private var isScrolling = false // Track if scrolling is active to prevent tap/swipe detection

    // Tap tracking
    private val tapTimestamps = LinkedList<Long>()
    private var tapStartTime = 0L
    private val longPressThresholdMs = 500L

    // Swipe tracking - using VelocityTracker for native velocity
    private var velocityTracker: VelocityTracker? = null
    private var swipeStartTime = 0L
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeLastX = 0f
    private var swipeLastY = 0f
    private var isSwipe = false
    private val swipeThresholdPx = 50.0f
    private val tapMovementTolerancePx = 10.0f // Allow small movement for taps
    private var previousSwipeVelocity = 0.0
    private var lastSwipeVelocityTime = 0L

    private val touchListener =
            View.OnTouchListener { view, event ->
                handleTouchEvent(event)
                false // Don't consume the event
            }

    override fun start() {
        // No-op, listeners are attached to views
    }

    override fun stop() {
        tapTimestamps.clear()
        velocityTracker?.recycle()
        velocityTracker = null
        scrollStopTimer?.removeCallbacksAndMessages(null)
        scrollStopTimer = null
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventCallback = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        // Placeholder for parity with SignalCollector interface
        return emptyMap()
    }

    override fun getSessionSummary(): Map<String, Any?> {
        // Placeholder for parity with SignalCollector interface
        return emptyMap()
    }

    override fun resetSession() {
        stop()
    }

    fun attachToView(view: View) {
        if (!config.enableInputSignals) {
            android.util.Log.d("GestureCollector", "Input signals disabled, not attaching")
            return
        }

        android.util.Log.d("GestureCollector", "Attaching to view: ${view.javaClass.simpleName}")
        // Attach touch listener for gesture detection
        view.setOnTouchListener(touchListener)
        android.util.Log.d("GestureCollector", "Touch listener attached")

        // Attach scroll listeners based on view type
        when (view) {
            is RecyclerView -> {
                android.util.Log.d("GestureCollector", "Attaching RecyclerView scroll listener")
                view.addOnScrollListener(
                        object : RecyclerView.OnScrollListener() {
                            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                android.util.Log.d(
                                        "GestureCollector",
                                        "RecyclerView scrolled: dy=$dy"
                                )
                                onScrollDelta(dy)
                            }
                        }
                )
            }
            is ScrollView -> {
                android.util.Log.d("GestureCollector", "Attaching ScrollView scroll listener")
                var lastScrollY = view.scrollY
                view.viewTreeObserver.addOnScrollChangedListener {
                    // Get scroll position from ScrollView and calculate delta
                    val currentScrollY = view.scrollY
                    val deltaY = currentScrollY - lastScrollY
                    android.util.Log.d(
                            "GestureCollector",
                            "ScrollView scrolled: deltaY=$deltaY, currentScrollY=$currentScrollY, lastScrollY=$lastScrollY"
                    )
                    if (deltaY != 0) {
                        onScrollDelta(deltaY)
                        lastScrollY = currentScrollY
                    }
                }
            }
            is NestedScrollView -> {
                android.util.Log.d("GestureCollector", "Attaching NestedScrollView scroll listener")
                var lastScrollY = view.scrollY
                view.viewTreeObserver.addOnScrollChangedListener {
                    // Get scroll position from NestedScrollView and calculate delta
                    val currentScrollY = view.scrollY
                    val deltaY = currentScrollY - lastScrollY
                    android.util.Log.d(
                            "GestureCollector",
                            "NestedScrollView scrolled: deltaY=$deltaY, currentScrollY=$currentScrollY, lastScrollY=$lastScrollY"
                    )
                    if (deltaY != 0) {
                        onScrollDelta(deltaY)
                        lastScrollY = currentScrollY
                    }
                }
            }
        }
    }

    fun updateConfig(newConfig: BehaviorConfig) {
        config = newConfig
    }

    private fun handleTouchEvent(event: MotionEvent) {
        android.util.Log.d(
                "GestureCollector",
                "handleTouchEvent: action=${event.action}, isScrolling=$isScrolling"
        )
        // Don't process touch events if scrolling is active (prevents scroll from being detected as
        // tap/swipe)
        if (isScrolling &&
                        event.action != MotionEvent.ACTION_UP &&
                        event.action != MotionEvent.ACTION_CANCEL
        ) {
            android.util.Log.d("GestureCollector", "Skipping touch event - scrolling is active")
            return
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                android.util.Log.d("GestureCollector", "ACTION_DOWN detected")
                // Only start tap/swipe tracking if not scrolling
                if (!isScrolling) {
                    tapStartTime = System.currentTimeMillis()
                    swipeStartTime = System.currentTimeMillis()
                    swipeStartX = event.x
                    swipeStartY = event.y
                    swipeLastX = event.x
                    swipeLastY = event.y
                    isSwipe = false
                    previousSwipeVelocity = 0.0
                    lastSwipeVelocityTime = swipeStartTime

                    // Initialize VelocityTracker for native velocity tracking
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                swipeLastX = event.x
                swipeLastY = event.y
                val deltaX = swipeLastX - swipeStartX
                val deltaY = swipeLastY - swipeStartY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                // If movement is significant, treat as swipe
                if (distance > swipeThresholdPx) {
                    isSwipe = true

                    // Track velocity changes during the gesture for acceleration calculation
                    val now = System.currentTimeMillis()
                    velocityTracker?.computeCurrentVelocity(1000)
                    val currentVelocityX = velocityTracker?.xVelocity ?: 0f
                    val currentVelocityY = velocityTracker?.yVelocity ?: 0f
                    val currentVelocity =
                            sqrt(
                                            currentVelocityX * currentVelocityX +
                                                    currentVelocityY * currentVelocityY
                                    )
                                    .toDouble()

                    if (lastSwipeVelocityTime > 0 && now > lastSwipeVelocityTime) {
                        val timeDelta = (now - lastSwipeVelocityTime) / 1000.0
                        if (timeDelta > 0) {
                            previousSwipeVelocity = currentVelocity
                        }
                    }
                    lastSwipeVelocityTime = now
                }
            }
            MotionEvent.ACTION_UP -> {
                // Don't process tap/swipe if scrolling is active
                if (isScrolling) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return
                }

                val duration = System.currentTimeMillis() - tapStartTime
                val swipeDuration = System.currentTimeMillis() - swipeStartTime

                // Check if it's a swipe
                val deltaX = swipeLastX - swipeStartX
                val deltaY = swipeLastY - swipeStartY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                // Determine if it's a swipe or tap
                // A swipe requires: significant movement (> threshold) AND sufficient duration (>=
                // 100ms)
                // A tap is: small movement OR very quick gesture (< 100ms) - quick taps are always
                // taps
                val isSwipeGesture = distance > swipeThresholdPx && swipeDuration >= 100

                if (isSwipeGesture && swipeDuration > 0) {
                    // It's a swipe - use native velocity from VelocityTracker
                    velocityTracker?.computeCurrentVelocity(1000) // pixels per second
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    val velocity = sqrt(velocityX * velocityX + velocityY * velocityY).toDouble()

                    // Calculate acceleration as change in velocity over time
                    // For a swipe starting from rest: a = (v_final - v_initial) / t
                    // Since initial velocity is 0, and assuming roughly constant acceleration:
                    // a ≈ v / t (but this can be large, so we'll use a more reasonable calculation)
                    val acceleration =
                            if (swipeDuration > 50 && previousSwipeVelocity > 0) {
                                // Use velocity change if we tracked it
                                (velocity - previousSwipeVelocity) / (swipeDuration / 1000.0)
                            } else if (swipeDuration > 50) {
                                // Fallback: average acceleration assuming constant acceleration
                                // from rest
                                // a = 2 * distance / t² (from d = 0.5 * a * t²)
                                (2.0 * distance) /
                                        ((swipeDuration / 1000.0) * (swipeDuration / 1000.0))
                            } else {
                                0.0
                            }

                    // Determine swipe direction
                    val direction =
                            when {
                                abs(deltaX) > abs(deltaY) -> if (deltaX > 0) "right" else "left"
                                else -> if (deltaY > 0) "down" else "up"
                            }

                    emitSwipeEvent(
                            direction = direction,
                            distancePx = distance.toDouble(),
                            durationMs = swipeDuration.toInt(),
                            velocity = velocity,
                            acceleration = acceleration
                    )
                } else {
                    // It's a tap - always emit for taps (including quick taps)
                    // Taps are: small movement OR quick gesture
                    val longPress = duration >= longPressThresholdMs
                    // Ensure minimum duration for very quick taps (at least 10ms)
                    val tapDuration = if (duration < 10) 10 else duration.toInt()
                    android.util.Log.d(
                            "GestureCollector",
                            "Emitting tap event: duration=$tapDuration, longPress=$longPress, distance=$distance"
                    )
                    emitTapEvent(tapDurationMs = tapDuration, longPress = longPress)
                }

                // Clean up velocity tracker
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
    }

    private fun onScrollDelta(dy: Int) {
        val now = System.currentTimeMillis()
        isScrolling = true // Mark that scrolling is active

        if (scrollStartTime == null) {
            // Scroll just started
            scrollStartTime = now
            scrollStartPosition = dy
            scrollEndPosition = dy
            lastScrollPosition = dy
            lastScrollTime = now
            lastScrollY = dy
            hasDirectionReversal = false
            return
        }

        val timeDelta = now - lastScrollTime
        if (timeDelta == 0L) {
            // Still update position even if time delta is 0
            scrollEndPosition = dy
            lastScrollPosition = dy
            lastScrollY = dy
            return
        }

        // Update end position on every scroll update
        scrollEndPosition = dy
        lastScrollPosition = dy

        // Calculate velocity from scroll delta (pixels per second)
        val deltaY = dy - lastScrollY
        val velocity = abs(deltaY) / timeDelta.toDouble() * 1000.0

        // Calculate acceleration (change in velocity over time)
        val acceleration =
                if (previousVelocity > 0 && timeDelta > 0) {
                    (velocity - previousVelocity) / (timeDelta / 1000.0)
                } else 0.0

        // Determine scroll direction
        val direction =
                when {
                    deltaY > 0 -> "down"
                    deltaY < 0 -> "up"
                    else -> lastScrollDirection ?: "down"
                }

        // Check for direction reversal
        if (lastScrollDirection != null && lastScrollDirection != direction) {
            hasDirectionReversal = true
        }

        // Update tracking variables
        previousVelocity = velocity
        lastScrollTime = now
        lastScrollY = dy
        lastScrollDirection = direction

        // Don't emit scroll events immediately - wait for scroll to stop
        // Cancel previous timer and start a new one
        scrollStopTimer?.removeCallbacksAndMessages(null)
        scrollStopTimer = android.os.Handler(android.os.Looper.getMainLooper())
        scrollStopTimer?.postDelayed({ finalizeScroll() }, scrollStopThresholdMs)
    }

    private fun finalizeScroll() {
        if (scrollStartTime == null) {
            return
        }

        val now = System.currentTimeMillis()
        val durationMs = now - scrollStartTime!!

        // Calculate distance correctly for both directions
        // For downward scroll: endPosition > startPosition (positive)
        // For upward scroll: endPosition < startPosition (negative, so abs() makes it positive)
        val rawDistance = scrollEndPosition - scrollStartPosition
        val distancePx = abs(rawDistance.toDouble())

        // Emit scroll event if there's any movement (even small)
        if (durationMs > 0 && distancePx >= 0) {
            // Calculate average velocity in pixels per second
            // Velocity = distance / time (always positive, direction is separate)
            val effectiveDistance = if (distancePx > 0) distancePx else 1.0
            val velocity = (effectiveDistance / durationMs * 1000.0).coerceIn(0.0, 10000.0)

            // Calculate acceleration using proper physics formula
            // For constant acceleration from rest: a = 2d/t²
            // where d = distance (pixels), t = time (seconds)
            val durationSeconds = durationMs / 1000.0
            val acceleration =
                    if (durationSeconds > 0.1) {
                        (2.0 * effectiveDistance) / (durationSeconds * durationSeconds)
                    } else {
                        0.0
                    }
            val clampedAcceleration = acceleration.coerceIn(0.0, 50000.0)

            // Determine direction from overall movement
            val direction =
                    if (rawDistance > 0) "down"
                    else if (rawDistance < 0) "up" else lastScrollDirection ?: "down"

            // Emit scroll event with calculated metrics
            android.util.Log.d(
                    "GestureCollector",
                    "Emitting scroll event: velocity=$velocity, direction=$direction"
            )
            emitScrollEvent(
                    velocity = velocity,
                    acceleration = clampedAcceleration,
                    direction = direction,
                    directionReversal = hasDirectionReversal
            )
        }

        // Reset scroll tracking
        scrollStartTime = null
        scrollStartPosition = 0
        scrollEndPosition = 0
        lastScrollPosition = 0
        lastScrollTime = 0L
        previousVelocity = 0.0
        lastScrollDirection = null
        hasDirectionReversal = false
        isScrolling = false
        scrollStopTimer = null
    }

    private fun getIsoTimestamp(): String {
        return Instant.now().toString()
    }

    private fun emitScrollEvent(
            velocity: Double,
            acceleration: Double,
            direction: String,
            directionReversal: Boolean
    ) {
        android.util.Log.d(
                "GestureCollector",
                "emitScrollEvent: sessionId=$sessionId, eventCallback=${if (eventCallback != null) "set" else "null"}"
        )
        eventCallback?.invoke(
                BehaviorEvent(
                        sessionId = sessionId,
                        timestamp = getIsoTimestamp(),
                        eventType = BehaviorEventType.SCROLL,
                        metrics =
                                mapOf(
                                        "velocity" to velocity,
                                        "acceleration" to acceleration,
                                        "direction" to direction,
                                        "direction_reversal" to directionReversal
                                )
                )
        )
                ?: android.util.Log.w(
                        "GestureCollector",
                        "eventCallback is null! Scroll event not emitted."
                )
    }

    private fun emitTapEvent(tapDurationMs: Int, longPress: Boolean) {
        tapTimestamps.add(System.currentTimeMillis())
        while (tapTimestamps.size > 50) {
            tapTimestamps.removeAt(0)
        }

        android.util.Log.d(
                "GestureCollector",
                "emitTapEvent: sessionId=$sessionId, eventCallback=${if (eventCallback != null) "set" else "null"}"
        )
        eventCallback?.invoke(
                BehaviorEvent(
                        sessionId = sessionId,
                        timestamp = getIsoTimestamp(),
                        eventType = BehaviorEventType.TAP,
                        metrics =
                                mapOf("tap_duration_ms" to tapDurationMs, "long_press" to longPress)
                )
        )
                ?: android.util.Log.w(
                        "GestureCollector",
                        "eventCallback is null! Tap event not emitted."
                )
    }

    private fun emitSwipeEvent(
            direction: String,
            distancePx: Double,
            durationMs: Int,
            velocity: Double,
            acceleration: Double
    ) {
        eventCallback?.invoke(
                BehaviorEvent(
                        sessionId = sessionId,
                        timestamp = getIsoTimestamp(),
                        eventType = BehaviorEventType.SWIPE,
                        metrics =
                                mapOf(
                                        "direction" to direction,
                                        "distance_px" to distancePx,
                                        "duration_ms" to durationMs,
                                        "velocity" to velocity,
                                        "acceleration" to acceleration
                                )
                )
        )
    }
}
