package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*
import kotlin.test.assertFailsWith

class BehaviorConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = BehaviorConfig()

        assertTrue(config.enableInputSignals)
        assertTrue(config.enableAttentionSignals)
        assertFalse(config.enableMotionLite)
        assertNull(config.sessionIdPrefix)
        assertEquals(10, config.eventBatchSize)
        assertEquals(10.0, config.maxIdleGapSeconds, 0.001)
    }

    @Test
    fun `custom config values are preserved`() {
        val config = BehaviorConfig(
            enableInputSignals = false,
            enableAttentionSignals = false,
            enableMotionLite = true,
            sessionIdPrefix = "TEST",
            eventBatchSize = 20,
            maxIdleGapSeconds = 15.0
        )

        assertFalse(config.enableInputSignals)
        assertFalse(config.enableAttentionSignals)
        assertTrue(config.enableMotionLite)
        assertEquals("TEST", config.sessionIdPrefix)
        assertEquals(20, config.eventBatchSize)
        assertEquals(15.0, config.maxIdleGapSeconds, 0.001)
    }

    @Test
    fun `negative eventBatchSize throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorConfig(eventBatchSize = -1)
        }
    }

    @Test
    fun `zero eventBatchSize throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorConfig(eventBatchSize = 0)
        }
    }

    @Test
    fun `negative maxIdleGapSeconds throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorConfig(maxIdleGapSeconds = -1.0)
        }
    }

    @Test
    fun `zero maxIdleGapSeconds throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorConfig(maxIdleGapSeconds = 0.0)
        }
    }

    @Test
    fun `toMap returns correct structure`() {
        val config = BehaviorConfig(
            enableInputSignals = true,
            enableAttentionSignals = false,
            enableMotionLite = true,
            sessionIdPrefix = "CUSTOM",
            eventBatchSize = 15,
            maxIdleGapSeconds = 20.0
        )

        val map = config.toMap()

        assertEquals(true, map["enableInputSignals"])
        assertEquals(false, map["enableAttentionSignals"])
        assertEquals(true, map["enableMotionLite"])
        assertEquals("CUSTOM", map["sessionIdPrefix"])
        assertEquals(15, map["eventBatchSize"])
        assertEquals(20.0, map["maxIdleGapSeconds"])
    }

    @Test
    fun `toMap handles null sessionIdPrefix`() {
        val config = BehaviorConfig(sessionIdPrefix = null)
        val map = config.toMap()

        assertEquals("", map["sessionIdPrefix"])
    }
}
