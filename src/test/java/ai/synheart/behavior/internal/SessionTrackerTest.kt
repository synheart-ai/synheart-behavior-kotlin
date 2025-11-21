package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SessionTrackerTest {

    private lateinit var tracker: SessionTracker
    private val sessionId = "test-session"

    @Before
    fun setup() {
        tracker = SessionTracker(sessionId)
    }

    @Test
    fun `tracker initializes with correct session ID`() {
        assertEquals(sessionId, tracker.sessionId)
    }

    @Test
    fun `event count starts at zero`() {
        assertEquals(0, tracker.getEventCount())
    }

    @Test
    fun `recordEvent increments event count`() {
        val event = createTestEvent(BehaviorEventType.TYPING_CADENCE)
        tracker.recordEvent(event)

        assertEquals(1, tracker.getEventCount())

        tracker.recordEvent(event)
        assertEquals(2, tracker.getEventCount())
    }

    @Test
    fun `recordEvent updates aggregated statistics for typing`() {
        val event = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.TYPING_CADENCE,
            payload = mapOf("keys_per_second" to 5.2)
        )

        tracker.recordEvent(event)

        val inputStats = mapOf("typingCadence" to 5.2)
        val summary = tracker.getSessionSummary(inputStats, emptyMap(), emptyMap())

        assertEquals(5.2, summary.averageTypingCadence!!, 0.001)
    }

    @Test
    fun `recordEvent tracks app switches`() {
        val event = createTestEvent(BehaviorEventType.APP_SWITCH)

        tracker.recordEvent(event)
        tracker.recordEvent(event)
        tracker.recordEvent(event)

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())
        assertEquals(3, summary.appSwitchCount)
    }

    @Test
    fun `getCurrentStats combines all collector stats`() {
        val inputStats = mapOf(
            "typingCadence" to 5.2,
            "scrollVelocity" to 300.0,
            "tapRate" to 2.5
        )
        val attentionStats = mapOf(
            "appSwitchesPerMinute" to 3,
            "stabilityIndex" to 0.85
        )
        val motionStats = emptyMap<String, Any?>()

        val stats = tracker.getCurrentStats(inputStats, attentionStats, motionStats)

        assertEquals(5.2, stats.typingCadence!!, 0.001)
        assertEquals(300.0, stats.scrollVelocity!!, 0.001)
        assertEquals(2.5, stats.tapRate!!, 0.001)
        assertEquals(3, stats.appSwitchesPerMinute)
        assertEquals(0.85, stats.stabilityIndex!!, 0.001)
    }

    @Test
    fun `getSessionSummary includes duration calculation`() {
        val startTime = tracker.getStartTimestamp()
        Thread.sleep(100) // Wait a bit

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        assertTrue(summary.duration >= 100)
        assertEquals(startTime, summary.startTimestamp)
        assertTrue(summary.endTimestamp >= startTime)
    }

    @Test
    fun `getSessionSummary aggregates typing cadences`() {
        val event1 = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.TYPING_CADENCE,
            payload = mapOf("keys_per_second" to 5.0)
        )
        val event2 = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.TYPING_CADENCE,
            payload = mapOf("keys_per_second" to 7.0)
        )

        tracker.recordEvent(event1)
        tracker.recordEvent(event2)

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        // Average of 5.0 and 7.0 = 6.0
        assertEquals(6.0, summary.averageTypingCadence!!, 0.001)
    }

    @Test
    fun `getSessionSummary aggregates scroll velocities`() {
        val event1 = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.SCROLL_VELOCITY,
            payload = mapOf("velocity_px_per_sec" to 200.0)
        )
        val event2 = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.SCROLL_VELOCITY,
            payload = mapOf("velocity_px_per_sec" to 400.0)
        )

        tracker.recordEvent(event1)
        tracker.recordEvent(event2)

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        // Average of 200.0 and 400.0 = 300.0
        assertEquals(300.0, summary.averageScrollVelocity!!, 0.001)
    }

    @Test
    fun `getSessionSummary handles stability and fragmentation indices`() {
        val stabilityEvent = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.SESSION_STABILITY,
            payload = mapOf("stability_index" to 0.9)
        )
        val fragmentationEvent = BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = BehaviorEventType.FRAGMENTATION_INDEX,
            payload = mapOf("fragmentation_index" to 0.2)
        )

        tracker.recordEvent(stabilityEvent)
        tracker.recordEvent(fragmentationEvent)

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        assertEquals(0.9, summary.stabilityIndex!!, 0.001)
        assertEquals(0.2, summary.fragmentationIndex!!, 0.001)
    }

    @Test
    fun `multiple events of same type update count correctly`() {
        val event = createTestEvent(BehaviorEventType.TAP_RATE)

        repeat(10) {
            tracker.recordEvent(event)
        }

        assertEquals(10, tracker.getEventCount())
    }

    @Test
    fun `getSessionSummary includes all collected metrics`() {
        tracker.recordEvent(createTestEvent(BehaviorEventType.TYPING_CADENCE))
        tracker.recordEvent(createTestEvent(BehaviorEventType.APP_SWITCH))

        val inputSummary = mapOf("totalKeystrokes" to 50)
        val attentionSummary = mapOf("totalForegroundTime" to 120000L)
        val motionSummary = emptyMap<String, Any?>()

        val summary = tracker.getSessionSummary(inputSummary, attentionSummary, motionSummary)

        assertEquals(sessionId, summary.sessionId)
        assertEquals(2, summary.eventCount)
        assertTrue(summary.duration >= 0)
    }

    private fun createTestEvent(type: BehaviorEventType): BehaviorEvent {
        return BehaviorEvent(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = type,
            payload = emptyMap()
        )
    }
}
