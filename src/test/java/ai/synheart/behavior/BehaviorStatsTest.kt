package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*
import kotlin.test.assertFailsWith

class BehaviorStatsTest {

    @Test
    fun `stats creation with all fields works`() {
        val timestamp = System.currentTimeMillis()
        val stats = BehaviorStats(
            typingCadence = 5.2,
            interKeyLatency = 150.0,
            burstLength = 10,
            scrollVelocity = 500.0,
            scrollAcceleration = 50.0,
            scrollJitter = 10.5,
            tapRate = 2.5,
            appSwitchesPerMinute = 3,
            foregroundDuration = 120.0,
            idleGapSeconds = 5.0,
            stabilityIndex = 0.85,
            fragmentationIndex = 0.25,
            timestamp = timestamp
        )

        assertEquals(5.2, stats.typingCadence!!, 0.001)
        assertEquals(150.0, stats.interKeyLatency!!, 0.001)
        assertEquals(10, stats.burstLength!!)
        assertEquals(500.0, stats.scrollVelocity!!, 0.001)
        assertEquals(50.0, stats.scrollAcceleration!!, 0.001)
        assertEquals(10.5, stats.scrollJitter!!, 0.001)
        assertEquals(2.5, stats.tapRate!!, 0.001)
        assertEquals(3, stats.appSwitchesPerMinute)
        assertEquals(120.0, stats.foregroundDuration!!, 0.001)
        assertEquals(5.0, stats.idleGapSeconds!!, 0.001)
        assertEquals(0.85, stats.stabilityIndex!!, 0.001)
        assertEquals(0.25, stats.fragmentationIndex!!, 0.001)
        assertEquals(timestamp, stats.timestamp)
    }

    @Test
    fun `stats with null optional fields works`() {
        val stats = BehaviorStats(
            timestamp = System.currentTimeMillis()
        )

        assertNull(stats.typingCadence)
        assertNull(stats.interKeyLatency)
        assertNull(stats.burstLength)
        assertNull(stats.scrollVelocity)
        assertNull(stats.scrollAcceleration)
        assertNull(stats.scrollJitter)
        assertNull(stats.tapRate)
        assertEquals(0, stats.appSwitchesPerMinute)
        assertNull(stats.foregroundDuration)
        assertNull(stats.idleGapSeconds)
        assertNull(stats.stabilityIndex)
        assertNull(stats.fragmentationIndex)
    }

    @Test
    fun `negative appSwitchesPerMinute throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStats(
                appSwitchesPerMinute = -1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `stabilityIndex below zero throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStats(
                stabilityIndex = -0.1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `stabilityIndex above one throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStats(
                stabilityIndex = 1.1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `fragmentationIndex below zero throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStats(
                fragmentationIndex = -0.1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `fragmentationIndex above one throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStats(
                fragmentationIndex = 1.1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `boundary values for indices are valid`() {
        val stats1 = BehaviorStats(
            stabilityIndex = 0.0,
            fragmentationIndex = 0.0,
            timestamp = System.currentTimeMillis()
        )
        assertEquals(0.0, stats1.stabilityIndex!!, 0.001)
        assertEquals(0.0, stats1.fragmentationIndex!!, 0.001)

        val stats2 = BehaviorStats(
            stabilityIndex = 1.0,
            fragmentationIndex = 1.0,
            timestamp = System.currentTimeMillis()
        )
        assertEquals(1.0, stats2.stabilityIndex!!, 0.001)
        assertEquals(1.0, stats2.fragmentationIndex!!, 0.001)
    }

    @Test
    fun `toMap includes all non-null fields`() {
        val stats = BehaviorStats(
            scrollVelocity = 500.0,
            appSwitchesPerMinute = 3,
            stabilityIndex = 0.85,
            timestamp = 123456L
        )

        val map = stats.toMap()

        assertEquals(500.0, map["scroll_velocity"])
        assertNull(map["scroll_acceleration"])
        assertNull(map["scroll_jitter"])
        assertNull(map["tap_rate"])
        assertEquals(3, map["app_switches_per_minute"])
        assertEquals(0.85, map["stability_index"])
        assertEquals(123456L, map["timestamp"])
    }

    @Test
    fun `toMap handles all null optional fields`() {
        val stats = BehaviorStats(timestamp = 123L)
        val map = stats.toMap()

        assertNull(map["scroll_velocity"])
        assertNull(map["tap_rate"])
        assertEquals(0, map["app_switches_per_minute"])
        assertEquals(123L, map["timestamp"])
    }
}
