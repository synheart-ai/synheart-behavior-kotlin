package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*
import kotlin.test.assertFailsWith

class BehaviorSessionTest {

    @Test
    fun `session creation with all fields works`() {
        val summary = BehaviorSessionSummary(
            sessionId = "test-session",
            startTimestamp = 1000L,
            endTimestamp = 5000L,
            duration = 4000L,
            eventCount = 100,
            averageTypingCadence = 5.2,
            averageScrollVelocity = 500.0,
            appSwitchCount = 3,
            stabilityIndex = 0.85,
            fragmentationIndex = 0.25
        )

        assertEquals("test-session", summary.sessionId)
        assertEquals(1000L, summary.startTimestamp)
        assertEquals(5000L, summary.endTimestamp)
        assertEquals(4000L, summary.duration)
        assertEquals(100, summary.eventCount)
        assertEquals(5.2, summary.averageTypingCadence!!, 0.001)
        assertEquals(500.0, summary.averageScrollVelocity!!, 0.001)
        assertEquals(3, summary.appSwitchCount)
        assertEquals(0.85, summary.stabilityIndex!!, 0.001)
        assertEquals(0.25, summary.fragmentationIndex!!, 0.001)
    }

    @Test
    fun `session with null optional fields works`() {
        val summary = BehaviorSessionSummary(
            sessionId = "test",
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            duration = 1000L
        )

        assertEquals(0, summary.eventCount)
        assertNull(summary.averageTypingCadence)
        assertNull(summary.averageScrollVelocity)
        assertEquals(0, summary.appSwitchCount)
        assertNull(summary.stabilityIndex)
        assertNull(summary.fragmentationIndex)
    }

    @Test
    fun `endTimestamp before startTimestamp throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 2000L,
                endTimestamp = 1000L,
                duration = 0L
            )
        }
    }

    @Test
    fun `negative duration throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = -1L
            )
        }
    }

    @Test
    fun `negative eventCount throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                eventCount = -1
            )
        }
    }

    @Test
    fun `negative appSwitchCount throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                appSwitchCount = -1
            )
        }
    }

    @Test
    fun `stabilityIndex below zero throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                stabilityIndex = -0.1
            )
        }
    }

    @Test
    fun `stabilityIndex above one throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                stabilityIndex = 1.1
            )
        }
    }

    @Test
    fun `fragmentationIndex below zero throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                fragmentationIndex = -0.1
            )
        }
    }

    @Test
    fun `fragmentationIndex above one throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorSessionSummary(
                sessionId = "test",
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                duration = 1000L,
                fragmentationIndex = 1.1
            )
        }
    }

    @Test
    fun `boundary values for indices are valid`() {
        val summary1 = BehaviorSessionSummary(
            sessionId = "test",
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            duration = 1000L,
            stabilityIndex = 0.0,
            fragmentationIndex = 0.0
        )
        assertEquals(0.0, summary1.stabilityIndex!!, 0.001)
        assertEquals(0.0, summary1.fragmentationIndex!!, 0.001)

        val summary2 = BehaviorSessionSummary(
            sessionId = "test",
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            duration = 1000L,
            stabilityIndex = 1.0,
            fragmentationIndex = 1.0
        )
        assertEquals(1.0, summary2.stabilityIndex!!, 0.001)
        assertEquals(1.0, summary2.fragmentationIndex!!, 0.001)
    }

    @Test
    fun `equal timestamps are valid`() {
        val summary = BehaviorSessionSummary(
            sessionId = "test",
            startTimestamp = 1000L,
            endTimestamp = 1000L,
            duration = 0L
        )
        assertEquals(1000L, summary.startTimestamp)
        assertEquals(1000L, summary.endTimestamp)
    }

    @Test
    fun `toMap includes all fields`() {
        val summary = BehaviorSessionSummary(
            sessionId = "test-123",
            startTimestamp = 1000L,
            endTimestamp = 5000L,
            duration = 4000L,
            eventCount = 50,
            averageTypingCadence = 5.0,
            averageScrollVelocity = 300.0,
            appSwitchCount = 2,
            stabilityIndex = 0.9,
            fragmentationIndex = 0.1
        )

        val map = summary.toMap()

        assertEquals("test-123", map["session_id"])
        assertEquals(1000L, map["start_timestamp"])
        assertEquals(5000L, map["end_timestamp"])
        assertEquals(4000L, map["duration"])
        assertEquals(50, map["event_count"])
        assertEquals(5.0, map["average_typing_cadence"])
        assertEquals(300.0, map["average_scroll_velocity"])
        assertEquals(2, map["app_switch_count"])
        assertEquals(0.9, map["stability_index"])
        assertEquals(0.1, map["fragmentation_index"])
    }

    @Test
    fun `toMap handles null optional fields`() {
        val summary = BehaviorSessionSummary(
            sessionId = "test",
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            duration = 1000L
        )

        val map = summary.toMap()

        assertEquals("test", map["session_id"])
        assertEquals(0, map["event_count"])
        assertNull(map["average_typing_cadence"])
        assertNull(map["average_scroll_velocity"])
        assertEquals(0, map["app_switch_count"])
        assertNull(map["stability_index"])
        assertNull(map["fragmentation_index"])
    }
}
