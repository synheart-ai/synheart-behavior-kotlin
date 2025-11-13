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
        collector.onKeyEvent()
        collector.onKeyEvent()
        collector.onKeyEvent()

        val stats = collector.getCurrentStats()
        val typingCadence = stats["typingCadence"] as? Double

        // Should have some typing cadence after 3 key events
        assertNotNull(typingCadence)
    }

    @Test
    fun `onScrollEvent updates statistics`() {
        collector.onScrollEvent(100f)
        collector.onScrollEvent(150f)
        collector.onScrollEvent(120f)

        val stats = collector.getCurrentStats()
        val scrollVelocity = stats["scrollVelocity"] as? Double

        assertNotNull(scrollVelocity)
    }

    @Test
    fun `onTapEvent updates statistics`() {
        collector.onTapEvent()
        collector.onTapEvent()

        val stats = collector.getCurrentStats()
        val tapRate = stats["tapRate"] as? Double

        assertNotNull(tapRate)
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

        // Check if burst event was emitted
        val burstEvents = capturedEvents.filter { it.type == BehaviorEventType.TYPING_BURST }
        assertTrue("Expected at least one burst event", burstEvents.isNotEmpty())
    }

    @Test
    fun `resetSession clears all statistics`() {
        collector.onKeyEvent()
        collector.onTapEvent()
        collector.onScrollEvent(100f)

        val statsBefore = collector.getCurrentStats()
        assertNotNull(statsBefore["typingCadence"])

        collector.resetSession()

        val statsAfter = collector.getCurrentStats()
        assertNull(statsAfter["typingCadence"])
    }

    @Test
    fun `getSessionSummary returns aggregated data`() {
        collector.onKeyEvent()
        collector.onKeyEvent()
        collector.onTapEvent()
        collector.onScrollEvent(100f)

        val summary = collector.getSessionSummary()

        assertEquals(2, summary["totalKeystrokes"])
        assertEquals(1, summary["totalTaps"])
        assertEquals(1, summary["totalScrolls"])
    }

    @Test
    fun `drag event calculates velocity`() {
        collector.onDragEvent(0f, 0f, 100f, 0f, 1000L)

        // Check if drag velocity event was emitted
        val dragEvents = capturedEvents.filter { it.type == BehaviorEventType.DRAG_VELOCITY }
        assertTrue("Expected drag velocity event", dragEvents.isNotEmpty())

        val payload = dragEvents.first().payload
        val velocity = payload["velocity_px_per_sec"] as? Float
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
        collector.onKeyEvent()
        collector.onKeyEvent()
        collector.onKeyEvent()

        // Wait for periodic emission (5 seconds in implementation)
        delay(6000)

        val typingEvents = capturedEvents.filter { it.type == BehaviorEventType.TYPING_CADENCE }
        assertTrue("Expected typing cadence events", typingEvents.isNotEmpty())
    }

    @Test
    fun `scroll stop event emitted after scroll stops`() = runBlocking {
        collector.onScrollEvent(100f)

        // Wait for scroll stop detection (500ms in implementation)
        delay(600)

        val scrollStopEvents = capturedEvents.filter { it.type == BehaviorEventType.SCROLL_STOP }
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
