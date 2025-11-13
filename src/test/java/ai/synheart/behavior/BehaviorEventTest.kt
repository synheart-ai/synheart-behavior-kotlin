package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*

class BehaviorEventTest {

    @Test
    fun `event creation preserves all fields`() {
        val sessionId = "test-session"
        val timestamp = System.currentTimeMillis()
        val type = BehaviorEventType.TYPING_CADENCE
        val payload = mapOf("keys_per_second" to 5.2, "count" to 10)

        val event = BehaviorEvent(
            sessionId = sessionId,
            timestamp = timestamp,
            type = type,
            payload = payload
        )

        assertEquals(sessionId, event.sessionId)
        assertEquals(timestamp, event.timestamp)
        assertEquals(type, event.type)
        assertEquals(payload, event.payload)
    }

    @Test
    fun `toMap converts event to correct structure`() {
        val event = BehaviorEvent(
            sessionId = "sess-123",
            timestamp = 1234567890L,
            type = BehaviorEventType.SCROLL_VELOCITY,
            payload = mapOf("velocity_px_per_sec" to 100.5)
        )

        val map = event.toMap()

        assertEquals("sess-123", map["session_id"])
        assertEquals(1234567890L, map["timestamp"])
        assertEquals("scroll_velocity", map["type"])
        assertTrue(map["payload"] is Map<*, *>)

        @Suppress("UNCHECKED_CAST")
        val payload = map["payload"] as Map<String, Any>
        assertEquals(100.5, payload["velocity_px_per_sec"])
    }

    @Test
    fun `event type names are correctly defined`() {
        assertEquals("TYPING_CADENCE", BehaviorEventType.TYPING_CADENCE.name)
        assertEquals("TYPING_BURST", BehaviorEventType.TYPING_BURST.name)
        assertEquals("SCROLL_VELOCITY", BehaviorEventType.SCROLL_VELOCITY.name)
        assertEquals("SCROLL_ACCELERATION", BehaviorEventType.SCROLL_ACCELERATION.name)
        assertEquals("SCROLL_JITTER", BehaviorEventType.SCROLL_JITTER.name)
        assertEquals("SCROLL_STOP", BehaviorEventType.SCROLL_STOP.name)
        assertEquals("TAP_RATE", BehaviorEventType.TAP_RATE.name)
        assertEquals("LONG_PRESS_RATE", BehaviorEventType.LONG_PRESS_RATE.name)
        assertEquals("DRAG_VELOCITY", BehaviorEventType.DRAG_VELOCITY.name)
        assertEquals("APP_SWITCH", BehaviorEventType.APP_SWITCH.name)
        assertEquals("FOREGROUND_DURATION", BehaviorEventType.FOREGROUND_DURATION.name)
        assertEquals("IDLE_GAP", BehaviorEventType.IDLE_GAP.name)
        assertEquals("MICRO_IDLE", BehaviorEventType.MICRO_IDLE.name)
        assertEquals("MID_IDLE", BehaviorEventType.MID_IDLE.name)
        assertEquals("TASK_DROP_IDLE", BehaviorEventType.TASK_DROP_IDLE.name)
        assertEquals("SESSION_STABILITY", BehaviorEventType.SESSION_STABILITY.name)
        assertEquals("FRAGMENTATION_INDEX", BehaviorEventType.FRAGMENTATION_INDEX.name)
        assertEquals("ORIENTATION_SHIFT", BehaviorEventType.ORIENTATION_SHIFT.name)
        assertEquals("SHAKE_PATTERN", BehaviorEventType.SHAKE_PATTERN.name)
        assertEquals("MICRO_MOVEMENT", BehaviorEventType.MICRO_MOVEMENT.name)
    }

    @Test
    fun `event type converts to lowercase in toMap`() {
        val event = BehaviorEvent(
            sessionId = "test",
            timestamp = 123L,
            type = BehaviorEventType.APP_SWITCH,
            payload = emptyMap()
        )

        val map = event.toMap()
        assertEquals("app_switch", map["type"])
    }

    @Test
    fun `empty payload is handled correctly`() {
        val event = BehaviorEvent(
            sessionId = "test",
            timestamp = 123L,
            type = BehaviorEventType.IDLE_GAP,
            payload = emptyMap()
        )

        val map = event.toMap()
        assertTrue(map["payload"] is Map<*, *>)

        @Suppress("UNCHECKED_CAST")
        val payload = map["payload"] as Map<String, Any>
        assertTrue(payload.isEmpty())
    }

    @Test
    fun `payload with multiple types is preserved`() {
        val payload = mapOf(
            "string_value" to "test",
            "int_value" to 42,
            "double_value" to 3.14,
            "boolean_value" to true
        )

        val event = BehaviorEvent(
            sessionId = "test",
            timestamp = 123L,
            type = BehaviorEventType.TYPING_CADENCE,
            payload = payload
        )

        assertEquals(payload, event.payload)
        assertEquals("test", event.payload["string_value"])
        assertEquals(42, event.payload["int_value"])
        assertEquals(3.14, event.payload["double_value"])
        assertEquals(true, event.payload["boolean_value"])
    }
}
