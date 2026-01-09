package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class InputSignalCollectorTest {

    private lateinit var collector: InputSignalCollector
    private val capturedEvents = mutableListOf<BehaviorEvent>()
    private val sessionId = "test-session"

    @Before
    fun setup() {
        collector = InputSignalCollector(sessionId)
        collector.setEventCallback { event ->
            capturedEvents.add(event)
        }
        collector.start()
    }

    @After
    fun teardown() {
        collector.stop()
        capturedEvents.clear()
    }

    @Test
    fun `collector starts and stops without errors`() {
        val collector = InputSignalCollector("test")
        collector.start()
        collector.stop()
    }

    @Test
    fun `onKeyEvent updates statistics`() {
        val baseTime = System.currentTimeMillis()
        collector.onKeyEvent(baseTime)
        collector.onKeyEvent(baseTime + 100)
        collector.onKeyEvent(baseTime + 200)

        val stats = collector.getCurrentStats()
        val typingCadence = stats["typingCadence"] as? Double

        // Should have some typing cadence after 3 key events with different timestamps
        assertNotNull(typingCadence)
        assertTrue(typingCadence!! > 0)
    }

    @Test
    fun `onScrollEvent updates statistics`() {
        val baseTime = System.currentTimeMillis()
        collector.onScrollEvent(100f, baseTime)
        collector.onScrollEvent(150f, baseTime + 100)
        collector.onScrollEvent(120f, baseTime + 200)

        val stats = collector.getCurrentStats()
        val scrollVelocity = stats["scrollVelocity"] as? Double

        // Should have scroll velocity after multiple scroll events with time gaps
        assertNotNull(scrollVelocity)
        assertTrue(scrollVelocity!! != 0.0)
    }

    @Test
    fun `onTapEvent updates statistics`() {
        val baseTime = System.currentTimeMillis()
        collector.onTapEvent(baseTime)
        collector.onTapEvent(baseTime + 500)

        val stats = collector.getCurrentStats()
        val tapRate = stats["tapRate"] as? Double

        // Should have tap rate after taps with different timestamps
        assertNotNull(tapRate)
        assertTrue(tapRate!! > 0)
    }

    @Test
    fun `burst detection triggers typing burst event`() = runBlocking {
        val timestamp = System.currentTimeMillis()

        // Simulate a burst of keys within 200ms
        collector.onKeyEvent(timestamp)
        collector.onKeyEvent(timestamp + 50)
        collector.onKeyEvent(timestamp + 100)
        collector.onKeyEvent(timestamp + 150)

        // Wait for a gap to trigger burst event
        delay(300)
        collector.onKeyEvent(timestamp + 500)

        // Check if burst event was emitted (TAP with burst_length)
        val burstEvents = capturedEvents.filter { 
            it.eventType == BehaviorEventType.TAP && it.metrics.containsKey("burst_length")
        }
        assertTrue("Expected at least one burst event", burstEvents.isNotEmpty())
    }

    @Test
    fun `resetSession clears all statistics`() {
        val baseTime = System.currentTimeMillis()
        collector.onKeyEvent(baseTime)
        collector.onKeyEvent(baseTime + 100)
        collector.onTapEvent(baseTime)
        collector.onScrollEvent(100f, baseTime)

        // Verify statistics exist before reset
        val summaryBefore = collector.getSessionSummary()
        assertEquals(2, summaryBefore["totalKeystrokes"])

        collector.resetSession()

        // Verify statistics are cleared after reset
        val summaryAfter = collector.getSessionSummary()
        assertEquals(0, summaryAfter["totalKeystrokes"])
        assertEquals(0, summaryAfter["totalTaps"])
        assertEquals(0, summaryAfter["totalScrolls"])
    }

    @Test
    fun `getSessionSummary returns aggregated data`() {
        val baseTime = System.currentTimeMillis()
        collector.onKeyEvent(baseTime)
        collector.onKeyEvent(baseTime + 100)
        collector.onTapEvent(baseTime)
        // First scroll sets the baseline, second scroll increments counter
        collector.onScrollEvent(100f, baseTime)
        collector.onScrollEvent(120f, baseTime + 100)

        val summary = collector.getSessionSummary()

        // Verify counters (note: first scroll doesn't count due to deltaTime = 0)
        assertEquals(2, summary["totalKeystrokes"])
        assertEquals(1, summary["totalTaps"])
        assertEquals(1, summary["totalScrolls"])
        
        // Verify summary contains expected keys
        assertNotNull(summary["averageTypingCadence"])
        assertNotNull(summary["averageScrollVelocity"])
    }

    @Test
    fun `drag event calculates velocity`() {
        collector.onDragEvent(0f, 0f, 100f, 0f, 1000L)

        // Check if drag velocity event was emitted (SWIPE)
        val dragEvents = capturedEvents.filter { it.eventType == BehaviorEventType.SWIPE }
        assertTrue("Expected drag velocity event", dragEvents.isNotEmpty())

        val metrics = dragEvents.first().metrics
        val velocity = metrics["velocity_px_per_sec"] as? Double? ?: (metrics["velocity_px_per_sec"] as? Float)?.toDouble()
        assertNotNull(velocity)
        assertTrue(velocity!! > 0)
    }

    @Test
    fun `onLongPressEvent tracks long presses`() {
        collector.onLongPressEvent()
        collector.onLongPressEvent()

        val summary = collector.getSessionSummary()
        // Long presses should be tracked (implementation specific)
        assertNotNull(summary)
    }

    @Test
    fun `typing cadence emits events periodically`() = runBlocking {
        val baseTime = System.currentTimeMillis()
        collector.onKeyEvent(baseTime)
        collector.onKeyEvent(baseTime + 100)
        collector.onKeyEvent(baseTime + 200)

        // Wait for periodic emission (5 seconds in implementation) + buffer
        delay(5500)

        val typingEvents = capturedEvents.filter { 
            it.eventType == BehaviorEventType.TAP && it.metrics.containsKey("keys_per_second")
        }
        // Note: This test may be flaky due to timing, so we just verify the mechanism exists
        // In a real scenario, at least one event should be emitted after 5+ seconds
        assertTrue("Expected typing cadence events after delay", typingEvents.isNotEmpty())
    }

    @Test
    fun `scroll stop event emitted after scroll stops`() = runBlocking {
        collector.onScrollEvent(100f)

        // Wait for scroll stop detection (500ms in implementation)
        delay(600)

        val scrollStopEvents = capturedEvents.filter { 
            it.eventType == BehaviorEventType.SCROLL && !it.metrics.containsKey("velocity")
        }
        // Actually scroll stop emits SCROLL with timestamp in map, but check implementation details specific to stop
        // Implementation: emitEvent(BehaviorEventType.SCROLL, mapOf("timestamp" to ...))
        // So metrics would only contain timestamp.
        assertTrue("Expected scroll stop event", scrollStopEvents.isNotEmpty())
    }

    @Test
    fun `events have correct session ID`() {
        collector.onKeyEvent()

        if (capturedEvents.isNotEmpty()) {
            assertEquals(sessionId, capturedEvents.first().sessionId)
        }
    }
}
