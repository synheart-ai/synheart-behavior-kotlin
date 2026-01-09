package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.WindowType
import java.time.Instant
import java.util.*

/**
 * Aggregates behavioral events into rolling time windows.
 *
 * Maintains separate windows for 30-second (short) and 5-minute (long) periods.
 */
internal class WindowAggregator {
    companion object {
        /** Duration of short window in milliseconds (30 seconds). */
        const val SHORT_WINDOW_MS = 30 * 1000L

        /** Duration of long window in milliseconds (5 minutes). */
        const val LONG_WINDOW_MS = 5 * 60 * 1000L
    }

    /** Events in the short window (30s). */
    private val shortWindowEvents = ArrayDeque<BehaviorEvent>()

    /** Events in the long window (5m). */
    private val longWindowEvents = ArrayDeque<BehaviorEvent>()

    /** Current time for window boundary calculations (milliseconds since epoch). */
    private var currentTime: Long = System.currentTimeMillis()

    /** Parse ISO 8601 timestamp to milliseconds since epoch. */
    private fun parseTimestamp(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /** Add a new event to both windows. */
    fun addEvent(event: BehaviorEvent) {
        val eventTime = parseTimestamp(event.timestamp)
        currentTime = eventTime

        // Add to short window
        shortWindowEvents.add(event)
        pruneOldEvents(shortWindowEvents, SHORT_WINDOW_MS)

        // Add to long window
        longWindowEvents.add(event)
        pruneOldEvents(longWindowEvents, LONG_WINDOW_MS)
    }

    /** Get all events in the specified window. */
    fun getWindowEvents(windowType: WindowType): List<BehaviorEvent> {
        return when (windowType) {
            WindowType.SHORT -> {
                pruneOldEvents(shortWindowEvents, SHORT_WINDOW_MS)
                shortWindowEvents.toList()
            }
            WindowType.LONG -> {
                pruneOldEvents(longWindowEvents, LONG_WINDOW_MS)
                longWindowEvents.toList()
            }
        }
    }

    /** Get the window duration in milliseconds. */
    fun getWindowDurationMs(windowType: WindowType): Long {
        return when (windowType) {
            WindowType.SHORT -> SHORT_WINDOW_MS
            WindowType.LONG -> LONG_WINDOW_MS
        }
    }

    /** Remove events older than the window duration. */
    private fun pruneOldEvents(events: ArrayDeque<BehaviorEvent>, windowDurationMs: Long) {
        val cutoffTime = currentTime - windowDurationMs
        while (events.isNotEmpty()) {
            val eventTime = parseTimestamp(events.first.timestamp)
            if (eventTime < cutoffTime) {
                events.removeFirst()
            } else {
                break
            }
        }
    }

    /** Clear all events from both windows. */
    fun clear() {
        shortWindowEvents.clear()
        longWindowEvents.clear()
    }

    /** Get the number of events in each window. */
    fun getEventCounts(): Map<WindowType, Int> {
        return mapOf(
                WindowType.SHORT to shortWindowEvents.size,
                WindowType.LONG to longWindowEvents.size
        )
    }
}
