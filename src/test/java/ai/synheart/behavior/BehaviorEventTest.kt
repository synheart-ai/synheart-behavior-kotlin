package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class BehaviorEventTest {

    private val sessionId = "test-session"
    private val timestampMock = "2023-01-01T10:00:00Z"

    @Test
    fun `event creation via constructor works`() {
        val event = BehaviorEvent(
            sessionId = sessionId,
            timestamp = timestampMock,
            eventType = BehaviorEventType.TAP,
            metrics = mapOf("key" to "value")
        )

        assertEquals(sessionId, event.sessionId)
        assertEquals(timestampMock, event.timestamp)
        assertEquals(BehaviorEventType.TAP, event.eventType)
        assertEquals("value", event.metrics["key"])
    }

    @Test
    fun `toJson returns correct map`() {
        val event = BehaviorEvent(
            eventId = "evt_123",
            sessionId = sessionId,
            timestamp = timestampMock,
            eventType = BehaviorEventType.SCROLL,
            metrics = mapOf("velocity" to 100.0)
        )

        val json = event.toJson()
        val eventMap = json["event"] as Map<String, Any>

        assertEquals("evt_123", eventMap["event_id"])
        assertEquals(sessionId, eventMap["session_id"])
        assertEquals(timestampMock, eventMap["timestamp"])
        assertEquals("scroll", eventMap["event_type"])
        assertEquals(100.0, (eventMap["metrics"] as Map<String, Any>)["velocity"])
    }

    @Test
    fun `toLegacyJson returns correct map`() {
        val event = BehaviorEvent(
            sessionId = sessionId,
            timestamp = timestampMock,
            eventType = BehaviorEventType.TAP,
            metrics = mapOf("duration" to 50)
        )

        val legacyJson = event.toLegacyJson()

        assertEquals(sessionId, legacyJson["session_id"])
        assertEquals("tap", legacyJson["type"])
        // Check timestamp logic: converts ISO to millis
        val expectedMillis = Instant.parse(timestampMock).toEpochMilli()
        assertEquals(expectedMillis, legacyJson["timestamp"])
        assertEquals(50, (legacyJson["payload"] as Map<String, Any>)["duration"])
    }

    @Test
    fun `factory methods create correct events`() {
        val scrollEvent = BehaviorEvent.scroll(sessionId, 100.0, 10.0, "down", false)
        assertEquals(BehaviorEventType.SCROLL, scrollEvent.eventType)
        assertEquals(100.0, scrollEvent.metrics["velocity"])

        val tapEvent = BehaviorEvent.tap(sessionId, 50, false)
        assertEquals(BehaviorEventType.TAP, tapEvent.eventType)
        assertEquals(50, tapEvent.metrics["tap_duration_ms"])

        val swipeEvent = BehaviorEvent.swipe(sessionId, "left", 200.0, 100, 500.0, 0.0)
        assertEquals(BehaviorEventType.SWIPE, swipeEvent.eventType)
        assertEquals("left", swipeEvent.metrics["direction"])
        
        val notifEvent = BehaviorEvent.notification(sessionId, "received")
        assertEquals(BehaviorEventType.NOTIFICATION, notifEvent.eventType)
        assertEquals("received", notifEvent.metrics["action"])
        
        val callEvent = BehaviorEvent.call(sessionId, "incoming")
        assertEquals(BehaviorEventType.CALL, callEvent.eventType)
        assertEquals("incoming", callEvent.metrics["action"])
        
        val typingEvent = BehaviorEvent.typing(
            sessionId = sessionId,
            typingTapCount = 50,
            typingSpeed = 5.2,
            meanInterTapIntervalMs = 192.3,
            typingCadenceVariability = 0.15,
            typingCadenceStability = 0.85,
            typingGapCount = 3,
            typingGapRatio = 0.1,
            typingBurstiness = 0.3,
            typingActivityRatio = 0.9,
            typingInteractionIntensity = 0.8,
            durationSeconds = 10,
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T10:00:10Z",
            deepTyping = true
        )
        assertEquals(BehaviorEventType.TYPING, typingEvent.eventType)
        assertEquals(50, typingEvent.metrics["typing_tap_count"])
        assertEquals(5.2, typingEvent.metrics["typing_speed"])
        assertEquals(true, typingEvent.metrics["deep_typing"])
    }

    @Test
    fun `typing event includes backspace and clipboard counts for Flux`() {
        val typingEvent = BehaviorEvent.typing(
            sessionId = sessionId,
            typingTapCount = 10,
            typingSpeed = 4.0,
            meanInterTapIntervalMs = 200.0,
            typingCadenceVariability = 0.1,
            typingCadenceStability = 0.9,
            typingGapCount = 0,
            typingGapRatio = 0.0,
            typingBurstiness = 0.2,
            typingActivityRatio = 0.95,
            typingInteractionIntensity = 0.8,
            durationSeconds = 5,
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T10:00:05Z",
            deepTyping = false,
            backspaceCount = 3,
            numberOfCopy = 1,
            numberOfPaste = 2,
            numberOfCut = 0
        )
        assertEquals(3, typingEvent.metrics["backspace_count"])
        assertEquals(0, typingEvent.metrics["number_of_delete"]) // Mobile: always 0
        assertEquals(1, typingEvent.metrics["number_of_copy"])
        assertEquals(2, typingEvent.metrics["number_of_paste"])
        assertEquals(0, typingEvent.metrics["number_of_cut"])
    }

    @Test
    fun `clipboard factory creates clipboard event`() {
        val event = BehaviorEvent.clipboard(sessionId, "paste", "textField")
        assertEquals(BehaviorEventType.CLIPBOARD, event.eventType)
        assertEquals("paste", event.metrics["action"])
        assertEquals("textField", event.metrics["context"])
    }

    @Test
    fun `fromJson parses correctly`() {
        val json = mapOf(
            "event" to mapOf(
                "event_id" to "evt_123",
                "session_id" to sessionId,
                "timestamp" to timestampMock,
                "event_type" to "swipe",
                "metrics" to mapOf("direction" to "up")
            )
        )

        val event = BehaviorEvent.fromJson(json)

        assertEquals("evt_123", event.eventId)
        assertEquals(sessionId, event.sessionId)
        assertEquals(timestampMock, event.timestamp)
        assertEquals(BehaviorEventType.SWIPE, event.eventType)
        assertEquals("up", event.metrics["direction"])
    }

    @Test
    fun `fromJson parses typing events correctly`() {
        val json = mapOf(
            "event" to mapOf(
                "event_id" to "evt_typing_123",
                "session_id" to sessionId,
                "timestamp" to timestampMock,
                "event_type" to "typing",
                "metrics" to mapOf(
                    "typing_tap_count" to 30,
                    "typing_speed" to 4.5,
                    "deep_typing" to true
                )
            )
        )

        val event = BehaviorEvent.fromJson(json)
        assertEquals("evt_typing_123", event.eventId)
        assertEquals(BehaviorEventType.TYPING, event.eventType)
        assertEquals(30, event.metrics["typing_tap_count"])
    }
}
