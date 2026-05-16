package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.BehaviorEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class InputSignalCollectorTest {

    private lateinit var collector: InputSignalCollector
    private val capturedEvents = mutableListOf<BehaviorEvent>()
    private val sessionId = "test-session"
    private val config = BehaviorConfig(enableInputSignals = true)

    @Before
    fun setup() {
        collector = InputSignalCollector(config, sessionId)
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
        val c = InputSignalCollector(BehaviorConfig(), "test")
        c.start()
        c.stop()
    }

    @Test
    fun `constructor accepts BehaviorConfig and sessionId`() {
        val c = InputSignalCollector(BehaviorConfig(enableInputSignals = false), "s1")
        assertNotNull(c)
    }

    @Test
    fun `getCurrentStats returns map`() {
        val stats = collector.getCurrentStats()
        assertNotNull(stats)
        // InputSignalCollector no longer tracks keystrokes/scroll/tap; stats are placeholder
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `getSessionSummary returns map`() {
        val summary = collector.getSessionSummary()
        assertNotNull(summary)
        assertTrue(summary.isEmpty())
    }

    @Test
    fun `resetSession does not throw`() {
        collector.resetSession()
    }

    @Test
    fun `setEventCallback is accepted`() {
        var received: BehaviorEvent? = null
        collector.setEventCallback { received = it }
        // No events are emitted by InputSignalCollector in current implementation (keystroke/scroll removed)
        assertNull(received)
    }

    @Test
    fun `updateConfig does not throw`() {
        collector.updateConfig(BehaviorConfig(enableInputSignals = false))
    }
}
