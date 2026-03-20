package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class SessionTrackerTest {

    private lateinit var tracker: SessionTracker
    private val sessionId = "test-session"

    private lateinit var context: Context
    private lateinit var resources: Resources

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        resources = mockk(relaxed = true)

        // Mock minimal context requirements to prevent NPE in init
        every { context.resources } returns resources
        every { resources.configuration } returns Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }

        tracker = SessionTracker(sessionId, context, 0L)
    }

    @After
    fun tearDown() {
        unmockkAll()
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
        val event = createTestEvent(BehaviorEventType.TAP)
        tracker.recordEvent(event)

        assertEquals(1, tracker.getEventCount())

        tracker.recordEvent(event)
        assertEquals(2, tracker.getEventCount())
    }

    @Test
    fun `recordEvent tracks app switches`() {
        tracker.updateAppSwitchCount(3)
        // Verify via session summary
        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())
        assertEquals(3, summary.activitySummary.appSwitchCount)
    }

    @Test
    fun `getCurrentStats combines all collector stats`() {
        val inputStats = mapOf(
            "scrollVelocity" to 300.0,
            "tapRate" to 2.5
        )
        val attentionStats = mapOf(
            "appSwitchesPerMinute" to 3,
            "stabilityIndex" to 0.85
        )
        val motionStats = emptyMap<String, Any?>()

        val stats = tracker.getCurrentStats(inputStats, attentionStats, motionStats)

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

        assertTrue(summary.durationMs >= 100)

        val summaryStart = try { Instant.parse(summary.startAt).toEpochMilli() } catch (e: Exception) { 0L }
        assertTrue(kotlin.math.abs(startTime - summaryStart) < 1000)
    }

    @Test
    fun `getSessionSummary handles stability and fragmentation indices via map`() {
        val attentionSummary = mapOf(
            "stabilityIndex" to 0.9,
            "fragmentationIndex" to 0.2
        )

        // Just verify method call doesn't crash
        val summary = tracker.getSessionSummary(emptyMap(), attentionSummary, emptyMap())
        assertNotNull(summary)
    }

    @Test
    fun `multiple events of same type update count correctly`() {
        val event = createTestEvent(BehaviorEventType.TAP)

        repeat(10) {
            tracker.recordEvent(event)
        }

        assertEquals(10, tracker.getEventCount())
    }

    @Test
    fun `getSessionSummary includes all collected metrics`() {
        tracker.recordEvent(createTestEvent(BehaviorEventType.TAP))
        tracker.recordEvent(createTestEvent(BehaviorEventType.TAP))

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        assertEquals(sessionId, summary.sessionId)
        assertEquals(2, summary.activitySummary.totalEvents)
        assertTrue(summary.durationMs >= 0)
    }

    @Test
    fun `getSessionSummary does not compute behavioral metrics locally`() {
        tracker.recordEvent(createTestEvent(BehaviorEventType.TAP))
        tracker.recordEvent(createTestEvent(BehaviorEventType.SCROLL))

        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        // Behavioral metrics (distraction_score, burstiness, etc.) are computed
        // by session-runtime, not the behavior SDK
        assertNull(summary.behavioralMetrics)
    }

    @Test
    fun `getSessionSummary includes typing session summary when typing events present`() {
        val typingEvent = BehaviorEvent.typing(
            sessionId = sessionId,
            typingTapCount = 30,
            typingSpeed = 5.0,
            meanInterTapIntervalMs = 200.0,
            typingCadenceVariability = 0.1,
            typingCadenceStability = 0.9,
            typingGapCount = 2,
            typingGapRatio = 0.05,
            typingBurstiness = 0.2,
            typingActivityRatio = 0.95,
            typingInteractionIntensity = 0.85,
            durationSeconds = 10,
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T10:00:10Z",
            deepTyping = true
        )

        tracker.recordEvent(typingEvent)
        val summary = tracker.getSessionSummary(emptyMap(), emptyMap(), emptyMap())

        assertNotNull(summary.typingSessionSummary)
        assertEquals(1, summary.typingSessionSummary!!.typingSessionCount)
        assertEquals(1, summary.typingSessionSummary!!.individualTypingSessions.size)
        assertEquals(30, summary.typingSessionSummary!!.individualTypingSessions[0].typingTapCount)
    }

    private fun createTestEvent(type: BehaviorEventType): BehaviorEvent {
        return BehaviorEvent(
            sessionId = sessionId,
            timestamp = Instant.now().toString(),
            eventType = type,
            metrics = emptyMap()
        )
    }
}
